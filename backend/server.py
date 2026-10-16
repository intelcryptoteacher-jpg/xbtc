"""XBTC development API: simulated ledger and a guarded Bitcoin Core RPC bridge.

Not an XBTC consensus node, real wallet, miner, or production exchange.
"""
from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import sqlite3
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, Field


@dataclass(frozen=True)
class Settings:
    db_path: str = "backend/xbtc-test.db"
    app_token: str = ""
    demo_mode: bool = False
    btc_rpc_url: str = "http://127.0.0.1:18443"
    btc_rpc_user: str = ""
    btc_rpc_password: str = ""
    btc_network: str = "regtest"
    btc_broadcast_enabled: bool = False

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            db_path=os.getenv("XBTC_DB_PATH", "backend/xbtc-test.db"),
            app_token=os.getenv("XBTC_APP_TOKEN", ""),
            demo_mode=os.getenv("XBTC_DEMO_MODE") == "1",
            btc_rpc_url=os.getenv("BTC_RPC_URL", "http://127.0.0.1:18443"),
            btc_rpc_user=os.getenv("BTC_RPC_USER", ""),
            btc_rpc_password=os.getenv("BTC_RPC_PASSWORD", ""),
            btc_network=os.getenv("BTC_RPC_NETWORK", "regtest"),
            btc_broadcast_enabled=os.getenv("BTC_BROADCAST_ENABLED") == "1",
        )


class Transfer(BaseModel):
    from_wallet: str
    to_wallet: str
    amount: int = Field(gt=0, le=1_000_000_000_000)
    idempotency_key: str = Field(min_length=8, max_length=128)


class Funding(BaseModel):
    wallet_id: str
    amount: int = Field(gt=0, le=1_000_000_000_000)


class RPCRequest(BaseModel):
    method: str
    params: list[Any] = Field(default_factory=list)
    id: str | int | None = None
    jsonrpc: str = "2.0"


BTC_READ_METHODS = frozenset({
    "getblockchaininfo", "getnetworkinfo", "getblockcount",
    "getbestblockhash", "getblockhash", "getblock",
    "getrawtransaction", "decoderawtransaction", "estimatesmartfee",
})
HEX = re.compile(r"^[0-9a-fA-F]+$")


def init_db(path: str) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(path, timeout=10) as conn:
        conn.executescript("""
            CREATE TABLE IF NOT EXISTS wallets (
                id TEXT PRIMARY KEY,
                balance INTEGER NOT NULL DEFAULT 0 CHECK(balance >= 0)
            );
            CREATE TABLE IF NOT EXISTS transfers (
                id TEXT PRIMARY KEY,
                from_wallet TEXT NOT NULL,
                to_wallet TEXT NOT NULL,
                amount INTEGER NOT NULL CHECK(amount > 0),
                idempotency_key TEXT NOT NULL UNIQUE,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE TABLE IF NOT EXISTS funding_audit (
                id TEXT PRIMARY KEY,
                wallet_id TEXT NOT NULL,
                amount INTEGER NOT NULL CHECK(amount > 0),
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            );
        """)


def db_connect(path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(path, timeout=10, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout = 10000")
    return conn


def bitcoin_rpc(settings: Settings, method: str, params: list[Any]) -> Any:
    parsed = urllib.parse.urlparse(settings.btc_rpc_url)
    # Credentials are forwarded only to loopback. Other hosts require a separate
    # deployment/security design, rather than blindly proxying a public endpoint.
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}:
        raise ValueError("Bitcoin Core RPC URL must use loopback HTTP")
    if not settings.btc_rpc_user or not settings.btc_rpc_password:
        raise ValueError("Bitcoin Core RPC credentials are not configured")
    authorization = base64.b64encode(
        (settings.btc_rpc_user + ":" + settings.btc_rpc_password).encode()
    ).decode()
    payload = json.dumps({"jsonrpc": "1.0", "id": "xbtc-bridge", "method": method, "params": params}).encode()
    req = urllib.request.Request(
        settings.btc_rpc_url, data=payload,
        headers={"Content-Type": "application/json", "Authorization": "Basic " + authorization},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            decoded = json.load(response)
    except (OSError, ValueError, urllib.error.HTTPError) as exc:
        raise ValueError("Bitcoin Core RPC unreachable or rejected the request") from exc
    if decoded.get("error") is not None:
        raise ValueError("Bitcoin Core RPC returned an error: " + str(decoded["error"].get("code", "unknown")))
    return decoded.get("result")


def rpc_result(payload: RPCRequest, result: Any = None, error: dict | None = None) -> dict:
    return {"jsonrpc": "2.0", "id": payload.id, "result": result if not error else None, "error": error}


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    init_db(settings.db_path)
    api = FastAPI(title="XBTC test backend and Bitcoin RPC adapter",
                  description="Development-only simulation; no real XBTC blockchain settlement.")

    def require_token(authorization: str | None = Header(default=None)) -> None:
        # Refuse operation until the operator configures authentication.
        import hmac
        if not settings.app_token or not authorization or not hmac.compare_digest(
            authorization, "Bearer " + settings.app_token
        ):
            raise HTTPException(status_code=401, detail="Valid XBTC_APP_TOKEN bearer token required")

    @api.get("/health")
    def health() -> dict:
        return {"status": "ok", "mode": "simulation", "native_xbtc_chain_ready": False}

    @api.post("/v1/test/wallets", dependencies=[Depends(require_token)])
    def create_wallet() -> dict:
        wallet_id = "test_" + uuid.uuid4().hex
        with db_connect(settings.db_path) as conn:
            conn.execute("INSERT INTO wallets (id) VALUES (?)", (wallet_id,))
        return {"wallet_id": wallet_id, "currency": "XBTC_TEST", "simulated": True}

    @api.get("/v1/test/wallets/{wallet_id}/balance", dependencies=[Depends(require_token)])
    def get_balance(wallet_id: str) -> dict:
        with db_connect(settings.db_path) as conn:
            row = conn.execute("SELECT balance FROM wallets WHERE id = ?", (wallet_id,)).fetchone()
        if row is None:
            raise HTTPException(404, "Test wallet not found")
        return {"wallet_id": wallet_id, "balance": row["balance"], "currency": "XBTC_TEST",
                "simulated": True}

    @api.post("/v1/test/fund", dependencies=[Depends(require_token)])
    def fund(data: Funding) -> dict:
        if not settings.demo_mode:
            raise HTTPException(403, "Demo funding disabled")
        with db_connect(settings.db_path) as conn:
            try:
                conn.execute("BEGIN IMMEDIATE")
                cursor = conn.execute(
                    "UPDATE wallets SET balance = balance + ? WHERE id = ? AND balance <= ?",
                    (data.amount, data.wallet_id, 9_223_372_036_854_775_807 - data.amount),
                )
                if cursor.rowcount != 1:
                    raise HTTPException(404, "Wallet not found or balance limit reached")
                audit_id = uuid.uuid4().hex
                conn.execute("INSERT INTO funding_audit(id,wallet_id,amount) VALUES(?,?,?)",
                             (audit_id, data.wallet_id, data.amount))
                conn.commit()
            except BaseException:
                conn.rollback()
                raise
        return {"funding_id": audit_id, "currency": "XBTC_TEST", "simulated": True}

    @api.post("/v1/test/transfers", dependencies=[Depends(require_token)])
    def transfer(data: Transfer) -> dict:
        if data.from_wallet == data.to_wallet:
            raise HTTPException(400, "Source and destination must differ")
        with db_connect(settings.db_path) as conn:
            try:
                conn.execute("BEGIN IMMEDIATE")
                existing = conn.execute(
                    "SELECT * FROM transfers WHERE idempotency_key = ?", (data.idempotency_key,)
                ).fetchone()
                if existing is not None:
                    if (existing["from_wallet"], existing["to_wallet"], existing["amount"]) != (
                        data.from_wallet, data.to_wallet, data.amount
                    ):
                        raise HTTPException(409, "Idempotency key already used for a different transfer")
                    conn.commit()
                    return {"transaction_id": existing["id"], "simulated": True,
                            "currency": "XBTC_TEST"}
                src = conn.execute("SELECT balance FROM wallets WHERE id=?", (data.from_wallet,)).fetchone()
                dst = conn.execute("SELECT balance FROM wallets WHERE id=?", (data.to_wallet,)).fetchone()
                if src is None or dst is None:
                    raise HTTPException(404, "Test wallet not found")
                if src["balance"] < data.amount:
                    raise HTTPException(409, "Insufficient simulated balance")
                if dst["balance"] > 9_223_372_036_854_775_807 - data.amount:
                    raise HTTPException(409, "Balance limit reached")
                conn.execute("UPDATE wallets SET balance = balance - ? WHERE id=?",
                             (data.amount, data.from_wallet))
                conn.execute("UPDATE wallets SET balance = balance + ? WHERE id=?",
                             (data.amount, data.to_wallet))
                txid = uuid.uuid4().hex
                conn.execute(
                    "INSERT INTO transfers(id,from_wallet,to_wallet,amount,idempotency_key)"
                    " VALUES(?,?,?,?,?)",
                    (txid, data.from_wallet, data.to_wallet, data.amount, data.idempotency_key),
                )
                conn.commit()
            except BaseException:
                conn.rollback()
                raise
        return {"transaction_id": txid, "currency": "XBTC_TEST", "simulated": True}

    @api.get("/v1/test/transactions/{transaction_id}", dependencies=[Depends(require_token)])
    def get_transfer(transaction_id: str) -> dict:
        with db_connect(settings.db_path) as conn:
            row = conn.execute("SELECT * FROM transfers WHERE id = ?", (transaction_id,)).fetchone()
        if row is None:
            raise HTTPException(404, "Test transaction not found")
        return {"transaction_id": row["id"], "from_wallet": row["from_wallet"],
                "to_wallet": row["to_wallet"], "amount": row["amount"],
                "currency": "XBTC_TEST", "simulated": True}

    @api.post("/rpc/xbtc", dependencies=[Depends(require_token)])
    def xbtc_rpc(payload: RPCRequest) -> dict:
        if payload.method == "xbtc.status":
            return rpc_result(payload, {"chain": "xbtc", "native_rpc": False,
                                        "native_transaction_broadcast": False,
                                        "mode": "simulation"})
        return rpc_result(payload, error={"code": -32601, "message": "Native XBTC RPC method not implemented"})

    @api.post("/rpc/bitcoin", dependencies=[Depends(require_token)])
    def bridge_rpc(payload: RPCRequest) -> dict:
        if payload.method not in BTC_READ_METHODS | {"sendrawtransaction"}:
            return rpc_result(payload, error={"code": -32601, "message": "Method not allowlisted"})
        if payload.method == "sendrawtransaction":
            if not settings.btc_broadcast_enabled:
                return rpc_result(payload, error={"code": -32001, "message": "Bitcoin broadcasting disabled"})
            if not payload.params or not isinstance(payload.params[0], str) or (
                len(payload.params[0]) % 2 != 0 or not HEX.fullmatch(payload.params[0])
            ):
                return rpc_result(payload, error={"code": -32602, "message": "Expected raw transaction hex"})
        try:
            info = bitcoin_rpc(settings, "getblockchaininfo", [])
            if info.get("chain") != settings.btc_network:
                raise ValueError("Configured Bitcoin network mismatch")
            result = info if payload.method == "getblockchaininfo" else bitcoin_rpc(
                settings, payload.method, payload.params
            )
            return rpc_result(payload, result)
        except ValueError as exc:
            return rpc_result(payload, error={"code": -32000, "message": str(exc)})

    return api


app = create_app()
