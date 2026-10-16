# XBTC test backend and Bitcoin Core RPC bridge

This project is **not** a deployed exchange, XBTC mainnet, wallet custody system,
mining pool or Bitcoin consensus implementation. The test ledger's balances are
\`XBTC_TEST\`, not transferable on any public blockchain.

## What is implemented

- A FastAPI service with \`GET /health\`.
- Authenticated test wallet IDs, integer-unit balances, and simulated transfers.
- Atomic SQLite journal updates, idempotency keys, insufficient-funds rejection,
  restart persistence and development-only test funding.
- A deliberately **limited** JSON-RPC-compatible bridge:
  - \`POST /rpc/xbtc\`: status only; native XBTC transaction RPC/broadcasting is not implemented.
  - \`POST /rpc/bitcoin\`: selected Bitcoin Core read-only methods; optional
    \`sendrawtransaction\` forwarding only when explicitly enabled.
- The Bitcoin bridge uses a separately running Bitcoin Core node on **loopback**.
  It verifies that the node's blockchain network matches the expected network.
  It does not make native XBTC blocks or Bitcoin and XBTC assets interchangeable.

## Local setup

Requirements: Python 3.11+ (CI uses 3.12).

From the repository root:

\`\`\`bash
python -m venv .venv
source .venv/bin/activate
python -m pip install -r backend/requirements.txt
export XBTC_APP_TOKEN="replace-with-a-long-random-local-secret"
export XBTC_DEMO_MODE=1
python -m uvicorn backend.server:app --host 127.0.0.1 --port 8000
\`\`\`

On Windows PowerShell replace \`source\` with \`.venv\Scripts\Activate.ps1\`;
configure the environment with \`$env:XBTC_APP_TOKEN="..."\` and
\`$env:XBTC_DEMO_MODE="1"\`.

You can run \`python -m pytest -q backend/tests\` from the repository root.
The test suite does **not** require Bitcoin Core, internet access, or paid services.

## API demonstration

With the server running, use the token configured above (never commit it).

\`\`\`bash
curl http://127.0.0.1:8000/health

curl -X POST http://127.0.0.1:8000/v1/test/wallets \
  -H "Authorization: Bearer $XBTC_APP_TOKEN"

# Repeat to get a second wallet ID; replace WALLET_A and WALLET_B below.
curl -X POST http://127.0.0.1:8000/v1/test/fund \
  -H "Authorization: Bearer $XBTC_APP_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"wallet_id":"WALLET_A","amount":1000}'

curl -X POST http://127.0.0.1:8000/v1/test/transfers \
  -H "Authorization: Bearer $XBTC_APP_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"from_wallet":"WALLET_A","to_wallet":"WALLET_B",
       "amount":250,"idempotency_key":"demo-transfer-001"}'

curl http://127.0.0.1:8000/v1/test/wallets/WALLET_B/balance \
  -H "Authorization: Bearer $XBTC_APP_TOKEN"
\`\`\`

Balances in this demonstration will be 750 and 250 **simulated units**.
The OpenAPI development contract is at \`http://127.0.0.1:8000/docs\`.

## Bitcoin Core compatibility (optional)

Run Bitcoin Core with \`-regtest -server\` and configure its RPC username and
password independently. Example settings for a local-only regtest instance:

\`\`\`bash
export BTC_RPC_URL="http://127.0.0.1:18443"
export BTC_RPC_USER="your-regtest-rpc-user"
export BTC_RPC_PASSWORD="your-regtest-rpc-password"
export BTC_RPC_NETWORK="regtest"
# Keep broadcasting disabled by default:
export BTC_BROADCAST_ENABLED=0
\`\`\`

\`\`\`bash
curl -X POST http://127.0.0.1:8000/rpc/bitcoin \
  -H "Authorization: Bearer $XBTC_APP_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"getblockchaininfo","params":[]}'
\`\`\`

The bridge only allows \`getblockchaininfo\`, \`getnetworkinfo\`,
\`getblockcount\`, \`getbestblockhash\`, \`getblockhash\`, \`getblock\`,
\`getrawtransaction\`, \`decoderawtransaction\`, \`estimatesmartfee\`,
and, **only if enabled**, \`sendrawtransaction\`. The latter requires a signed
raw transaction in hex prepared separately; the bridge does not generate,
sign or validate a transaction itself. The Bitcoin Core node performs its own
validation. **Never** enable Bitcoin broadcast on mainnet without a separate
security review, authentication architecture, and operator authorization.
The API is only bound to loopback in the documented command.

## Environment variables

| Name | Default | Purpose |
| --- | --- | --- |
| \`XBTC_DB_PATH\` | \`backend/xbtc-test.db\` | Persistent SQLite development database |
| \`XBTC_APP_TOKEN\` | unset, deny requests | Bearer token for non-health APIs |
| \`XBTC_DEMO_MODE\` | \`0\` | Enable fake test balance funding only when \`1\` |
| \`BTC_RPC_URL\` | \`http://127.0.0.1:18443\` | Loopback Bitcoin Core RPC |
| \`BTC_RPC_USER\`, \`BTC_RPC_PASSWORD\` | unset | Credentials for Bitcoin Core RPC |
| \`BTC_RPC_NETWORK\` | \`regtest\` | Expected Core chain: \`regtest\`, \`test\` or \`main\` |
| \`BTC_BROADCAST_ENABLED\` | \`0\` | Explicitly allow forwarding signed raw Bitcoin transactions |

Avoid exposing the API directly to the public internet. No credentials,
wallet recovery phrases, transaction secrets, or local DB files belong in git.

## Acceptance and next milestones

The automated tests cover the core mock ledger and mocked Bitcoin Core
interface. Integration with a **real** local Bitcoin Core regtest node remains
a separate test. Native XBTC transaction creation, mempool validation,
mining, chain consensus, P2P broadcasting and peer compatibility are NOT
implemented by this adapter.

For the Android app: use a clear clean dashboard, simulated / on-chain
status labels, and an **unavailable** XBTC market price until a real,
independently verifiable pricing source exists. Connect the Android interface
to a properly deployed, authenticated backend only after contract tests pass.
The existing \`.github/workflows/android-apk.yml\` is an Android APK build
configuration, not proof of successful installation or live mining.
