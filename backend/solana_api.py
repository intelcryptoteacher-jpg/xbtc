"""Read-only Solana connection for XBTC. No private keys, signed transactions or minting endpoints."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from typing import Any, Callable

from fastapi import APIRouter, Depends, HTTPException

SOLANA_RPC = {
    "devnet": "https://api.devnet.solana.com",
    "mainnet-beta": "https://api.mainnet-beta.solana.com",
}
SPL_TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
EXPECTED_SUPPLY_BASE_UNITS = 10_000_000_000 * 10**6


def network_config() -> tuple[str, str]:
    network = os.getenv("XBTC_SOLANA_NETWORK", "devnet")
    if network not in SOLANA_RPC:
        raise HTTPException(503, "Invalid XBTC_SOLANA_NETWORK configuration")
    return network, SOLANA_RPC[network]


def rpc_call(rpc_url: str, method: str, params: list[Any] | None = None) -> Any:
    # The URL is always selected from SOLANA_RPC, never provided by an API caller.
    payload = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method,
                          "params": params or []}).encode("utf-8")
    req = urllib.request.Request(rpc_url, data=payload,
                                 headers={"Content-Type": "application/json"},
                                 method="POST")
    try:
        with urllib.request.urlopen(req, timeout=6) as response:
            data = json.load(response)
    except (OSError, ValueError, urllib.error.HTTPError) as exc:
        raise HTTPException(503, "Solana RPC unavailable") from exc
    if not isinstance(data, dict) or "error" in data or "result" not in data:
        raise HTTPException(503, "Solana RPC rejected query")
    return data["result"]


def valid_solana_address(value: str) -> bool:
    alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    if not isinstance(value, str) or len(value) > 44 or len(value) < 32:
        return False
    n = 0
    for ch in value:
        if ch not in alphabet:
            return False
        n = n * 58 + alphabet.index(ch)
    byte_count = (n.bit_length() + 7) // 8
    leading_zeros = len(value) - len(value.lstrip("1"))
    return leading_zeros + byte_count == 32


def create_solana_router(authorize: Callable) -> APIRouter:
    router = APIRouter(prefix="/v1/solana", dependencies=[Depends(authorize)])

    @router.get("/status")
    def solana_status() -> dict:
        network, url = network_config()
        version = rpc_call(url, "getVersion")
        genesis_hash = rpc_call(url, "getGenesisHash")
        return {"network": network, "connected": True, "genesis_hash": genesis_hash,
                "core_version": version.get("solana-core") if isinstance(version, dict) else None,
                "signing_enabled": False, "minting_enabled": False}

    @router.get("/xbtc")
    def xbtc_mint_status() -> dict:
        network, url = network_config()
        address = os.getenv("XBTC_SOLANA_MINT_ADDRESS", "").strip()
        if not address:
            return {"network": network, "configured": False, "verified": False,
                    "note": "No XBTC mint address has been configured. No on-chain supply has been verified."}
        if not valid_solana_address(address):
            raise HTTPException(503, "Invalid configured XBTC_SOLANA_MINT_ADDRESS")
        info = rpc_call(url, "getAccountInfo", [address, {"encoding": "base64", "commitment": "confirmed"}])
        if not isinstance(info, dict) or not isinstance(info.get("value"), dict):
            return {"network": network, "mint_address": address, "configured": True,
                    "verified": False, "note": "Mint account not found on selected network"}
        if info["value"].get("owner") != SPL_TOKEN_PROGRAM:
            return {"network": network, "mint_address": address, "configured": True,
                    "verified": False, "note": "Address is not owned by the standard SPL Token Program"}
        supply_response = rpc_call(url, "getTokenSupply", [address, {"commitment": "confirmed"}])
        value = supply_response.get("value", {}) if isinstance(supply_response, dict) else {}
        try:
            amount = int(value["amount"])
            decimals = int(value["decimals"])
            if amount < 0:
                raise ValueError()
        except (ValueError, KeyError, TypeError) as exc:
            raise HTTPException(503, "Incomplete Solana mint supply response") from exc
        return {"network": network, "mint_address": address, "configured": True,
                "verified": True, "supply_base_units": str(amount),
                "supply_tokens": value.get("uiAmountString", str(amount)),
                "decimals": decimals, "matches_planned_10b_supply":
                amount == EXPECTED_SUPPLY_BASE_UNITS and decimals == 6,
                "explorer_url": ("https://explorer.solana.com/address/" + address +
                                 ("?cluster=devnet" if network == "devnet" else "")),
                "note": "Public on-chain balance only. Not proof of art backing, ownership or market value."}

    return router
