# XBTC Android 0.3 – marketplace, mining experiment and launch gate

Version 0.3 retains the real-asset intake preview for **artwork, gold and silver**, private local drafts, seller proceeds calculator, and zero fabricated public listings.

## Phone mining control

The **Phone mining test** tab starts a capped *foreground-only* native Android SHA-256 proof-of-work benchmark. It uses one CPU thread, a unique local challenge, a 12-leading-zero-bit demonstration target, paced batches, a low-battery gate (<25%), a hard 30-second session cap, an immediate Stop control, and automatic cancellation when the app moves to the background.

The app displays attempted hashes and an example successful digest and nonce when one is found. This is an educational algorithm experiment, **not Bitcoin mining, no XBTC consensus/network participation, no deposited coins, no block issuance, and no mining rewards**. The UI always indicates that XBTC earned is **zero**.

The original commercial subscription proposal (€1 per TH/s per week) is clearly separated from phone hash testing. Subscriptions and PayPal checkout remain disabled. Mobile SHA-256 test hashes cannot be equated to a delivered TH/s service.

## Wallet and Explorer

The Wallet tab accepts a transaction signature and provides a link to a Solana **devnet** transaction explorer. It validates the format locally but cannot verify transaction success itself. No real wallet is connected, no seed phrases are requested, and nothing is issued or traded in this Android build.

## Regulatory release gate

The **Launch review** tab explains the required identity/AML controls, cryptoasset classification, seller title verification, client disclosures, authorisation/provider requirements, custody, settlement and fulfilment. Real crypto-to-EUR conversion, paid mining, exchange trading, customer deposits and public auctions remain unavailable.

A phone call promising MiCA approval does not count as an authorisation. The operator must check the applicable Belgian and EU rules for the actual activities and verify any licence or regulated-service partner before launch.

## Further work

- Independently verify real seller titles, documents, custody and physical delivery.
- Design and validate native XBTC consensus **or** register a distinct mainnet token with a verifiable mint address and enforceable transfer rights.
- Set up server-enforced authenticated listings, auction rules, bids, escrow, anti-manipulation controls, real receipts and seller payouts.
- Integrate compliant regulated settlement providers and verify regulatory permissions.
- Conduct device/thermal testing and a security audit before releasing any full mining client.

## Build

Use [Android GitHub Actions](../.github/workflows/android-apk.yml). A successful run publishes `XBTC-Art-Exchange-Miner-APK` containing `app-debug.apk`. This is a debug preview, not a production-signed application.
