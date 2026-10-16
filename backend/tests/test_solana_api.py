"""Mocked tests: no tokens or SOL are spent and no public RPC is contacted."""
import pytest
from fastapi.testclient import TestClient

from backend import solana_api
from backend.server import Settings, create_app

AUTH = {"Authorization": "Bearer unit-test-secret"}
MINT = "So11111111111111111111111111111111111111112"


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("XBTC_SOLANA_NETWORK", "devnet")
    monkeypatch.delenv("XBTC_SOLANA_MINT_ADDRESS", raising=False)
    return TestClient(create_app(Settings(db_path=str(tmp_path / "solana.db"),
                                  app_token="unit-test-secret")))


def test_solana_endpoints_require_backend_auth(client):
    assert client.get("/v1/solana/status").status_code == 401
    assert client.get("/v1/solana/xbtc").status_code == 401
    assert client.post("/v1/solana/xbtc", headers=AUTH).status_code == 405


def test_status_checks_actual_rpc_methods(client, monkeypatch):
    methods = []

    def fake_rpc(url, method, params=None):
        assert url == "https://api.devnet.solana.com"
        methods.append(method)
        return {"solana-core": "test-node"} if method == "getVersion" else "devnet-genesis-hash"

    monkeypatch.setattr(solana_api, "rpc_call", fake_rpc)
    response = client.get("/v1/solana/status", headers=AUTH)
    assert response.status_code == 200
    assert response.json()["connected"] is True
    assert response.json()["minting_enabled"] is False
    assert methods == ["getVersion", "getGenesisHash"]


def test_unconfigured_mint_never_claims_supply(client):
    body = client.get("/v1/solana/xbtc", headers=AUTH).json()
    assert body["configured"] is False and body["verified"] is False


def test_rpc_verified_supply_requires_correct_program(client, monkeypatch):
    monkeypatch.setenv("XBTC_SOLANA_MINT_ADDRESS", MINT)

    def fake_rpc(_url, method, params=None):
        if method == "getAccountInfo":
            assert params[0] == MINT
            return {"value": {"owner": solana_api.SPL_TOKEN_PROGRAM}}
        if method == "getTokenSupply":
            return {"value": {"amount": "10000000000000000", "decimals": 6,
                              "uiAmountString": "10000000000"}}
        raise AssertionError(method)

    monkeypatch.setattr(solana_api, "rpc_call", fake_rpc)
    result = client.get("/v1/solana/xbtc", headers=AUTH).json()
    assert result["verified"] is True
    assert result["matches_planned_10b_supply"] is True
    assert result["supply_tokens"] == "10000000000"
    assert result["network"] == "devnet"


def test_wrong_program_is_not_verified(client, monkeypatch):
    monkeypatch.setenv("XBTC_SOLANA_MINT_ADDRESS", MINT)
    monkeypatch.setattr(solana_api, "rpc_call",
                        lambda _url, _method, _params: {"value": {"owner": "other"}})
    body = client.get("/v1/solana/xbtc", headers=AUTH).json()
    assert body["verified"] is False


def test_incorrect_supply_not_a_match(client, monkeypatch):
    monkeypatch.setenv("XBTC_SOLANA_MINT_ADDRESS", MINT)

    def fake_rpc(_url, method, _params=None):
        if method == "getAccountInfo":
            return {"value": {"owner": solana_api.SPL_TOKEN_PROGRAM}}
        return {"value": {"amount": "5", "decimals": 6, "uiAmountString": "0.000005"}}

    monkeypatch.setattr(solana_api, "rpc_call", fake_rpc)
    body = client.get("/v1/solana/xbtc", headers=AUTH).json()
    assert body["verified"] is True and body["matches_planned_10b_supply"] is False


def test_invalid_configuration_fails_closed(client, monkeypatch):
    monkeypatch.setenv("XBTC_SOLANA_NETWORK", "http://attacker.invalid")
    assert client.get("/v1/solana/status", headers=AUTH).status_code == 503
    monkeypatch.setenv("XBTC_SOLANA_NETWORK", "devnet")
    monkeypatch.setenv("XBTC_SOLANA_MINT_ADDRESS", "invalid://not-an-address")
    assert client.get("/v1/solana/xbtc", headers=AUTH).status_code == 503


def test_base58_pubkey_length_validation():
    assert solana_api.valid_solana_address(MINT)
    assert not solana_api.valid_solana_address("0" * 44)
    assert not solana_api.valid_solana_address("1" * 12)
