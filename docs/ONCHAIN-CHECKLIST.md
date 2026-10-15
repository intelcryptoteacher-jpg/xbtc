# XBTC on-chain readiness

- [x] Add Android APK project and automated build.
- [ ] Obtain successful signed APK artifact and test installation on Android.
- [ ] Connect a user-controlled Solana wallet; verify the public address and network. Never collect recovery phrases.
- [ ] Decide token decimals, intended 10 billion supply, mint authority, and freeze authority policy. Check integer limits before encoding supply.
- [ ] Implement mint creation, token accounts, minting and transfers on devnet, with user wallet signing.
- [ ] Verify rejected signatures, insufficient fees, retries, network errors, and confirmed transaction results.
- [ ] Configure production RPC, secure authentication and durable transaction records.
- [ ] Implement payment verification: correct network, mint, amount, recipient and finality; reject duplicate transaction credit.
- [ ] Establish artwork identity, ownership evidence, custody, redemption terms and token-holder rights; assess applicable launch requirements before customer sales.
- [ ] Fund the selected mainnet wallet with enough SOL for fees and account deposits.
- [ ] Review and approve mainnet mint transactions in the wallet. Verify mint address, supply and authorities independently on chain.
- [ ] Test a small mainnet transfer and payment before opening customer payments.

Minting alone does not establish a token price, liquidity, artwork ownership, or a functioning exchange. No mainnet mint or payment is verified by this checklist.
