# XBTC Exchange Android preview
Installable hybrid Android app containing the existing exchange interface.
Not a production exchange: account authentication, billing, custody, swaps and minting are not implemented as live services.
Internet is required for the existing interface's CSS, icons and charts.
Build with JDK 17, Gradle 8.9 and Android SDK 35: copy exchange/index.html to app/src/main/assets/index.html, then run gradle -p android assembleDebug from the repository root.
GitHub Actions performs this automatically. The APK is debug-signed for testing; production signing and device testing remain required.
CSV downloads and printing from the existing web interface are not integrated into Android.
