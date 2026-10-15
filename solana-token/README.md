# XBTC Solana devnet issuance and transfers

This module uses the standard SPL Token program. It creates a separate Solana token; it does not claim the existing BRC-20 XBTC listing or connect the legacy C++ miner to Solana.

## Run

Use Node 22 or later. Run `npm ci`, `npm test`, and `npm run build` in this directory. Serve the repository over HTTPS (or localhost for desktop development), then open `solana-token/index.html` in a supported Solana wallet browser. A Python local preview can use `python3 -m http.server 8000` from the repository root. Public deployment is separate.

Select devnet in Solflare, Backpack or Phantom. Connect the wallet and fund it with devnet SOL from an official faucet. The UI never asks for wallet keys. Review issuance in your wallet, then save the mint address and transaction signature. Load that exact mint to transfer tokens. No mainnet endpoint is configurable.

## Supply and authority

The atomic issuance transaction creates a mint, initializes nine decimals with no freeze authority, creates the owner's associated token account, mints exactly 10,000,000,000 tokens and permanently revokes mint authority. Base units are 10,000,000,000,000,000,000, below u64 max. All amount calculations use BigInt. Holders can still burn their own tokens using other clients, reducing supply.

Transfers use checked SPL instructions and idempotently create the recipient associated token account. Displayed balances come from that account on devnet, never the exchange simulation. The app checks token program ownership via the SPL SDK and checks decimals and authority settings; identical settings do not establish token identity. The saved mint address is authoritative.

## Limitations and acceptance

Automated tests cover amount precision, invalid inputs, instruction encoding, authority removal, recipient creation and issuance transaction size. They do not prove live chain execution. Wallet connection, rejection, devnet issuance, transfer, reload and recovery require a funded devnet wallet and an end-to-end test. No token has been issued by writing or building this module.

Wallet metadata/name/logo registration, artwork custody/legal rights, authentication, payment processing, mainnet deployment, and token pricing are not implemented here. The UI's XBTC label is local; wallets may display the mint address. Uncertain submissions lock further writes pending recovery; inspect Explorer before attempting a new issuance. Public RPC may rate-limit. Browser-local mint records are not a cross-device registry.

References: https://solana.com/docs/tokens/basics/create-mint ; https://solana.com/docs/tokens/basics/mint-tokens ; https://solana.com/docs/tokens/basics/transfer-tokens
