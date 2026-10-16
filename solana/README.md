# XBTC — real Solana SPL token operator workflow

**Status:** Implementation prepared. No mainnet token was deployed or minted by committing these files. These steps require an operator-controlled wallet, SOL to pay fees, and a real signature on the operator's device.

This folder is separate from the educational C++ Bitcoin-like implementation and the SQLite XBTC_TEST simulation. They are **not** the Solana token.

## Token parameters

- Working name: **XBTC**, XBTC project by Shine Art Ltd (the SPL mint alone does not publish a visible token name or ticker).
- Program: original Solana SPL Token Program (not Token-2022).
- Planned total: exactly **10,000,000,000 tokens**.
- Precision: **6 decimals**, corresponding to **10,000,000,000,000,000 base units**, inside the SPL u64 limit.
- Mint authority: the operator's wallet until intentionally and irrevocably revoked.
- Freeze authority: none.
- Initial allocation: all newly minted units go to the operator wallet's **associated token account**.
- Network: **devnet** for tests by default; **mainnet-beta** requires explicit authorization.
- Price, BTC conversion, reserve assets, art certificates, public trading and mining: **not provided** by minting a token.

## Requirements

1. Windows 11/macOS/Linux with Node.js 20+ and a locally installed Solana CLI. Official Solana setup: https://solana.com/docs/intro/installation
2. **A Solana operator wallet** that you own and can authorize with a local Solana CLI keypair file. This is NOT the same as a Bitcoin address or a simulated in-app wallet ID. Do **not** send anyone your JSON keypair, seed phrase, recovery phrase, or private key; never put one in GitHub or ChatGPT.
3. A small amount of devnet SOL (testing) or real SOL (mainnet) to cover account rent and transactions.
4. A careful assessment of any applicable EU/Belgian token-offering, custody, and art-asset disclosure rules before public sale.

## Windows PowerShell: devnet

From the repository directory:

```powershell
cd solana
npm install
# Set this path to YOUR pre-existing Solana CLI operator wallet JSON file:
$env:XBTC_OPERATOR_KEYPAIR = "C:\Users\YOURNAME\.config\solana\id.json"
$env:XBTC_NETWORK = "devnet"

npm run plan
npm run create
npm run status
npm run mint
npm run status
```

**No mint happens on `npm run plan` or `npm run status`.** `npm run create` submits a live devnet transaction creating a zero-supply mint; `npm run mint` submits a different signed transaction creating **10 billion XBTC devnet units**. Both require your locally controlled operator wallet. A rerun of `mint` refuses if the existing mint supply is nonzero.

The public mint address and a separate mint-account keypair are saved **before broadcasting** under `~/.xbtc-solana/devnet/` (not in the repository). This avoids accidental duplicate mints on retry. Protect this folder, especially the mint-account keypair file. The actual mint authority is your operator wallet.

If you have no operator wallet yet, create one locally using the Solana CLI and fund it on devnet first. NEVER paste its secret/recovery information into a chat or repository.

### Mainnet — only once you have verified your devnet deployment

Mainnet uses a completely separate token address and wallet balances. Do not claim testnet tokens are tradeable mainnet tokens.

```powershell
$env:XBTC_NETWORK = "mainnet-beta"
$env:XBTC_MAINNET_APPROVED = "I_AUTHORIZE_REAL_XBTC_MINT"
$env:XBTC_OPERATOR_KEYPAIR = "C:\Users\YOURNAME\.config\solana\id.json"
npm run plan
npm run create
npm run status
npm run mint
npm run status
```

A live mainnet mint costs real SOL and is publicly visible and difficult/impossible to reverse. The script will not run modifying mainnet commands without the exact authorization string. **Do not perform these commands before reviewing the on-chain authority settings, backing claims and launch plan.**

### Permanently lock the total supply (optional separate action)

The successful `mint` step leaves the mint authority active to allow recovery from incomplete operations. As long as mint authority is active, the token supply is **not permanently capped**, even if it presently equals 10B. If you have independently verified the full supply and want a truly fixed cap, a separate irreversible action exists:

```powershell
$env:XBTC_REVOKE_APPROVED = "I_ACCEPT_PERMANENT_REVOCATION"
npm run revoke
npm run status
```

This permanently destroys minting authority. It is impossible to mint more XBTC using the same SPL mint after this operation. The script refuses revocation unless the total on-chain supply already equals 10 billion tokens.

## Link the read-only exchange backend

The existing FastAPI app now exposes two authenticated, read-only endpoints:

- `GET /v1/solana/status`: contacts Solana RPC to check network connectivity; this does not establish token ownership.
- `GET /v1/solana/xbtc`: checks an explicitly configured mint account, SPL Token Program ownership, and on-chain supply; it will **not** claim that the supply is verified before an address is configured.

Set local backend environment variables (do not commit your bearer token):

```text
XBTC_SOLANA_NETWORK=devnet
XBTC_SOLANA_MINT_ADDRESS=<actual-public-mint-address-from-create>
XBTC_APP_TOKEN=<your-existing-long-random-local-secret>
```

The backend has **no minting route** and no wallet private key. It must be deployed behind appropriate authentication before exposing it to Android clients; the local WebView app is not yet connected to this API. The official public RPC is rate-limited and intended for development, not high-volume production use.

## Verification checklist

1. `npm run status` shows the actual public mint address, correct Solana cluster, 6 decimals, and supply.
2. A [Solana Explorer](https://explorer.solana.com/) lookup matches the address and cluster and displays the operator wallet's token account.
3. A true 10B current supply is not yet a permanent fixed supply until mint authority has been revoked.
4. XBTC display name, logo, on-chain metadata, verified art custody/ownership, exchange settlement, and paywall functionality are separate unimplemented work items.
5. No third-party XBTC tokens with the same ticker should be considered interchangeable: **the mint address is the unique identifier**.
6. Before accepting customers' money, obtain a security audit and relevant Belgium/EU compliance review.

## Local tests

```bash
node --test solana/config.test.mjs
node --check solana/xbtc.mjs
python -m pytest -q backend/tests
```

These tests and CI cannot attest to a real token deployment; the minting scripts must be run with a real wallet, and all actual transactions must be independently verified in the block explorer.
