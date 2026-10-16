# XBTC Android Art & Real Assets Marketplace (v0.2 preview)

This Android app is a functional **offline intake and fee-preview interface**, not yet a deployed exchange. The public marketplace intentionally starts **empty**. There are no fabricated paintings, gold bars, silver bars, prices, owners or on-chain certifications.

## What works in this APK
- Clean mobile navigation: Marketplace, Sell an asset, My assets, Fees, XBTC wallet.
- Owners can prepare and save private drafts for **real artwork/paintings, physical gold and physical silver**.
- Each private draft records asset category, name, description, optional serial or certificate reference, and proposed reserve in XBTC.
- Drafts persist locally via Android WebView storage, can be reviewed and deleted, and never appear on the public marketplace.
- Auction commission calculator shows hypothetical seller proceeds and exchange fees using a percentage manually entered by the user (no fabricated commission set).
- The interface clearly labels unavailable mining, payments, market rates, wallet balances, public bids, blockchain verification and custody.

## What is NOT implemented
- Owner authentication and upload of verification documents; no staff verification or approval system.
- A backend-driven public auction catalogue, order book, bid locking, escrow, final settlement, EUR-to-XBTC conversion, custody and delivery.
- Real token issuance, mainnet trading, Android mining, PayPal billing, or live gold and silver reference prices.
- Verified ownership of listed assets and enforceable transfer of legal title.

The current exchange backend in `backend/` contains a **simulated** ledger only; it cannot settle real artwork auctions. No real customer payment is accepted or sent.

## Requirements before releasing actual assets
1. Verify seller identity, title, authenticity, legal restrictions, custody, shipping, insurance and withdrawal/cancellation terms.
2. Approve the asset in an independently authenticated server-side registry; never approve an asset from client-side JavaScript.
3. Define a fixed-supply XBTC token and verified wallet integration; test on a public testnet, verify the mint address and issuance in an explorer.
4. Integrate a regulated money/payment provider to support confirmed euro receipts, conversion, refunds, disputes, payouts, AML checks and consumer protections.
5. Enforce server-side auctions with atomic bidding, funds verification, reserve and end-time checks, fraud and self-trading controls, fee disclosures and audit logs.
6. Release physical assets only after cleared payments, legal ownership transfer and delivery authorization—not upon a local button press or an unconfirmed transaction.

## Build
GitHub Actions workflow: [Build XBTC Exchange APK](../.github/workflows/android-apk.yml).

Run it on GitHub Actions, then open the successful run and download the `XBTC-Art-Exchange-Android-APK` artifact. Extract `app-debug.apk` from the ZIP and install it on Android if you trust the source and accept the Android debug-build limitations. It is not a production-signed release.

No seeded test items or artificial inventory is bundled with the APK.
