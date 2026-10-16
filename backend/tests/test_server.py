"""Development-only API regression tests. All assets are simulated."""
import sqlite3

import pytest
from fastapi.testclient import TestClient

from backend.server import Settings, create_app


@pytest.fixture
def settings(tmp_path):
    return Settings(
        db_path=str(tmp_path / "xbtc-test.sqlite3"),
        app_token="ci-demo-token",
        demo_mode=True,
        btc_network="regtest",
    )


@pytest.fixture
def client(settings):
    with TestClient(create_app(settings)) as api:
        yield api


HEADERS = {"Authorization": "Bearer ci-demo-token"}


def wallet(client):
    response = client.post("/v1/test/wallets", headers=HEADERS)
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["simulated"] is True
    assert body["currency"] == "XBTC_TEST"
    return body["wallet_id"]


def balance(client, wallet_id):
    response = client.get(f"/v1/test/wallets/{wallet_id}/balance", headers=HEADERS)
    assert response.status_code == 200
    assert response.json()["simulated"] is True
    return response.json()["balance"]


def funding(client, wallet_id, amount=1000):
    response = client.post("/v1/test/fund", headers=HEADERS,
                           json={"wallet_id": wallet_id, "amount": amount})
    assert response.status_code == 200, response.text
    assert response.json()["simulated"] is True


def test_health_requires_no_token_and_is_explicitly_simulated(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["mode"] == "simulation"
    assert response.json()["native_xbtc_chain_ready"] is False


def test_endpoints_require_a_valid_token(client):
    assert client.post("/v1/test/wallets").status_code == 401
    assert client.post("/v1/test/wallets",
                       headers={"Authorization": "Bearer incorrect"}).status_code == 401
    assert client.post("/rpc/xbtc", json={"method": "xbtc.status"}).status_code == 401


def test_missing_token_setting_denies_every_write(tmp_path):
    app = create_app(Settings(db_path=str(tmp_path / "missing.sqlite3")))
    with TestClient(app) as api:
        assert api.post("/v1/test/wallets",
                        headers={"Authorization": "Bearer anything"}).status_code == 401


def test_wallet_creation_balance_and_not_found(client):
    a = wallet(client)
    assert balance(client, a) == 0
    assert client.get("/v1/test/wallets/nonexistent/balance",
                      headers=HEADERS).status_code == 404


def test_happy_path_atomic_transfer_and_transaction_lookup(client, settings):
    a, b = wallet(client), wallet(client)
    funding(client, a, 1000)
    request = {"from_wallet": a, "to_wallet": b, "amount": 250,
               "idempotency_key": "transfer-0001"}
    response = client.post("/v1/test/transfers", headers=HEADERS, json=request)
    assert response.status_code == 200, response.text
    txid = response.json()["transaction_id"]
    assert balance(client, a) == 750
    assert balance(client, b) == 250

    detail = client.get(f"/v1/test/transactions/{txid}", headers=HEADERS)
    assert detail.status_code == 200
    assert detail.json()["from_wallet"] == a
    assert detail.json()["to_wallet"] == b
    assert detail.json()["amount"] == 250
    assert detail.json()["simulated"] is True

    # One durable journal entry, not only updated balance counters.
    with sqlite3.connect(settings.db_path) as db:
        assert db.execute("SELECT count(*) FROM transfers").fetchone()[0] == 1


def test_idempotency_replay_is_exactly_once(client, settings):
    a, b = wallet(client), wallet(client)
    funding(client, a)
    request = {"from_wallet": a, "to_wallet": b, "amount": 250,
               "idempotency_key": "replay-test-0001"}
    first = client.post("/v1/test/transfers", headers=HEADERS, json=request)
    second = client.post("/v1/test/transfers", headers=HEADERS, json=request)
    assert first.status_code == second.status_code == 200
    assert first.json()["transaction_id"] == second.json()["transaction_id"]
    assert (balance(client, a), balance(client, b)) == (750, 250)
    with sqlite3.connect(settings.db_path) as db:
        assert db.execute("SELECT count(*) FROM transfers").fetchone()[0] == 1
    changed = dict(request, amount=500)
    assert client.post("/v1/test/transfers", headers=HEADERS,
                       json=changed).status_code == 409


def test_insufficient_funds_does_not_modify_ledger(client, settings):
    a, b = wallet(client), wallet(client)
    funding(client, a)
    response = client.post("/v1/test/transfers", headers=HEADERS,
                           json={"from_wallet": a, "to_wallet": b, "amount": 1001,
                                 "idempotency_key": "insufficient-0001"})
    assert response.status_code == 409
    assert (balance(client, a), balance(client, b)) == (1000, 0)
    with sqlite3.connect(settings.db_path) as db:
        assert db.execute("SELECT count(*) FROM transfers").fetchone()[0] == 0


@pytest.mark.parametrize("amount", [0, -1, 1_000_000_000_001])
def test_invalid_amount_is_rejected(client, amount):
    a, b = wallet(client), wallet(client)
    result = client.post("/v1/test/transfers", headers=HEADERS,
                         json={"from_wallet": a, "to_wallet": b, "amount": amount,
                               "idempotency_key": "bad-amount-0001"})
    assert result.status_code == 422


def test_self_transfer_and_nonexistent_wallet(client):
    a = wallet(client)
    response = client.post("/v1/test/transfers", headers=HEADERS,
                           json={"from_wallet": a, "to_wallet": a, "amount": 1,
                                 "idempotency_key": "self-transfer-0001"})
    assert response.status_code == 400
    response = client.post("/v1/test/transfers", headers=HEADERS,
                           json={"from_wallet": a, "to_wallet": "invalid", "amount": 1,
                                 "idempotency_key": "missing-dest-0001"})
    assert response.status_code == 404


def test_persistence_after_restart(client, settings):
    a, b = wallet(client), wallet(client)
    funding(client, a, 1000)
    transfer = client.post("/v1/test/transfers", headers=HEADERS,
                           json={"from_wallet": a, "to_wallet": b, "amount": 250,
                                 "idempotency_key": "persist-transfer-0001"})
    txid = transfer.json()["transaction_id"]
    with TestClient(create_app(settings)) as restarted:
        assert balance(restarted, a) == 750
        assert balance(restarted, b) == 250
        assert restarted.get(f"/v1/test/transactions/{txid}",
                             headers=HEADERS).json()["amount"] == 250


def test_funding_disabled_by_default(tmp_path):
    settings = Settings(db_path=str(tmp_path / "disabled.sqlite3"), app_token="ci-demo-token")
    with TestClient(create_app(settings)) as api:
        a = wallet(api)
        response = api.post("/v1/test/fund", headers=HEADERS,
                            json={"wallet_id": a, "amount": 1000})
        assert response.status_code == 403
        assert balance(api, a) == 0


def test_native_xbtc_rpc_is_explicitly_not_implemented(client):
    request = {"jsonrpc": "2.0", "id": 1, "method": "xbtc.status"}
    status = client.post("/rpc/xbtc", headers=HEADERS, json=request).json()
    assert status["result"]["native_rpc"] is False
    assert status["result"]["native_transaction_broadcast"] is False
    unknown = client.post("/rpc/xbtc", headers=HEADERS,
                          json={"id": 2, "method": "sendrawtransaction"}).json()
    assert unknown["error"]["code"] == -32601


def test_bitcoin_bridge_denies_unallowlisted_and_broadcast_by_default(client):
    forbidden = client.post("/rpc/bitcoin", headers=HEADERS,
                            json={"id": 1, "method": "stop"}).json()
    assert forbidden["error"]["code"] == -32601
    disabled = client.post("/rpc/bitcoin", headers=HEADERS,
                           json={"id": 2, "method": "sendrawtransaction",
                                 "params": ["00"]}).json()
    assert disabled["error"]["code"] == -32001


def test_bitcoin_rpc_network_guard(monkeypatch, client):
    import backend.server as server
    monkeypatch.setattr(server, "bitcoin_rpc",
                        lambda settings, method, params: {"chain": "main", "blocks": 9})
    result = client.post("/rpc/bitcoin", headers=HEADERS,
                         json={"id": 3, "method": "getblockchaininfo"}).json()
    assert result["error"]["code"] == -32000
    assert "network mismatch" in result["error"]["message"]


def test_bitcoin_rpc_mock_regtest_read_only(monkeypatch, client):
    import backend.server as server
    monkeypatch.setattr(server, "bitcoin_rpc",
                        lambda settings, method, params: (
                            {"chain": "regtest", "blocks": 9} if method == "getblockchaininfo" else 9
                        ))
    result = client.post("/rpc/bitcoin", headers=HEADERS,
                         json={"id": 4, "method": "getblockcount"}).json()
    assert result["result"] == 9
    assert result["error"] is None



def test_test_backend_never_broadcasts_or_changes_bitcoin_balances(monkeypatch, tmp_path):
    """A stale BTC_BROADCAST_ENABLED=1 setting must never permit real BTC writes."""
    import backend.server as server

    calls = []

    def rpc_spy(settings, method, params):
        calls.append((method, params))
        return {"chain": "regtest"}

    monkeypatch.setattr(server, "bitcoin_rpc", rpc_spy)
    guarded = Settings(
        db_path=str(tmp_path / "bitcoin-read-only.sqlite3"),
        app_token="ci-demo-token",
        demo_mode=True,
        btc_network="regtest",
        btc_broadcast_enabled=True,
    )
    with TestClient(create_app(guarded)) as api:
        for method, params, error in [
            ("sendrawtransaction", ["deadbeef"], -32001),
            ("sendtoaddress", ["any", 1.0], -32601),
            ("sendmany", [], -32601),
            ("settxfee", [0.1], -32601),
            ("createwallet", ["unsafe"], -32601),
            ("invalidateblock", ["0" * 64], -32601),
            ("abandontransaction", ["0" * 64], -32601),
            ("stop", [], -32601),
        ]:
            response = api.post(
                "/rpc/bitcoin", headers=HEADERS,
                json={"id": 100, "method": method, "params": params},
            )
            assert response.status_code == 200
            assert response.json()["error"]["code"] == error
    # Rejected calls must not reach a Bitcoin node, even for network checks.
    assert calls == []
