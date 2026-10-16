# XBTC exchange backend — MVP checklist

**Status:** Planning. The items below are NOT yet implemented.
**Scope:** a test-only API for wallet IDs, balances, and simulated transfers. No mining, real XBTC, custody or live settlement.

The repository's existing C++ README describes syncing/verifying/storing Bitcoin-like blocks, and identifies transaction creation, mining and a local RPC server as missing. The root CMake file builds a C++ library and CLI executable. This first backend should be an isolated prototype, not a claim that the blockchain is production-ready.

## Priority 1 — Build the test backend
- [ ] Create a standalone `backend/` directory with FastAPI (Python) and SQLite.
- [ ] Implement `GET /health` (HTTP 200).
- [ ] Implement `POST /v1/test/wallets`: create a random simulated wallet **ID**, not a private key or real crypto address.
- [ ] Implement `GET /v1/test/wallets/{id}/balance`: integer units, currency `XBTC_TEST`, `simulated: true`.
- [ ] Implement `POST /v1/test/transfers`: transfer units between two test wallets with a required idempotency key.
- [ ] Implement `GET /v1/test/transactions/{id}`: inspect a simulated transfer.
- [ ] Provide a funding/seed fixture **only for local development and automated tests**; never expose it publicly.

## Priority 2 — Integrity and security
- [ ] Persist wallets and transfer journal in SQLite between restarts.
- [ ] Use one database transaction for both balances and the journal entry (all or nothing).
- [ ] Reject zero/negative amounts, insufficient balance, unknown wallet IDs and self-transfers.
- [ ] Make transfer requests idempotent: resubmitting the same key cannot debit twice.
- [ ] Tests cover creation, reading, valid transfer, invalid transfer, deduplication, rollback and persistence.
- [ ] Keep test endpoints local or behind appropriate authentication; commit no passwords, keys or payment secrets.
- [ ] Label all responses and documentation as **test-only simulation**, not on-chain funds.

## Priority 3 — Reproducible delivery
- [ ] Write `backend/README.md` with exact installation, run and `curl` usage examples.
- [ ] Add GitHub Actions CI to run backend tests on pushes and pull requests.
- [ ] Include safe example configuration without secrets.
- [ ] Document the API contract for later Android integration.

## Acceptance criteria
1. Clean checkout -> documented local command starts the backend.
2. Health endpoint succeeds; two test wallets are created.
3. Test fixture credits wallet A with 1,000 `XBTC_TEST` units.
4. Transfer 250 units to wallet B -> wallet A has 750, B has 250; transfer is retrievable.
5. Repeating the transfer with the same idempotency key changes nothing.
6. Attempting to transfer more than the available balance fails without changing balances.
7. After restarting the service, balances and transaction journal persist.
8. All automated tests pass in GitHub Actions.

## Later phases — NOT part of MVP
- [ ] Verify the intended XBTC blockchain / token contract, its ownership, consensus and network access; then design signed on-chain settlement.
- [ ] Connect a real wallet flow (Ledger / UniSat as supported by that network).
- [ ] Implement verified miner or mining pool integration; distinguish actual TH/s from simulated counters.
- [ ] Build PayPal recurring payments and properly verified webhooks only after assessing feasibility and compliance of the proposed €1 per 1 TH/s for one week subscription.
- [ ] Build Android UI and reproducible debug APK pipeline.
- [ ] Conduct security and Belgium/EU regulatory reviews before accepting customer payments.

> **Definition of done:** a tested local simulation, *not* a deployed exchange, real token, or working miner.

**Note:** GitHub Issues was disabled on this repository when this checklist was prepared. To use it as an actual issue later, enable **Settings → General → Features → Issues**, then copy this file into a new issue.
