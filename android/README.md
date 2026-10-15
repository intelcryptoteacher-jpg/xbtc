# XBTC Exchange Android demo
Android wrapper around the repository's existing exchange/index.html. Requires Android 8 or newer.
The native banner labels simulated prices, trading and mining as demo activity. This is not a production wallet or exchange.
HTML is bundled; third-party scripts and fonts still require internet. Data is stored only in this app on this device.
Browser wallet extensions are unavailable inside Android WebView. File upload, printing, downloads and popup flows need further native integration.
No seed phrases, private keys, native JavaScript bridge, file access or cleartext HTTP are provided.
Build: Gradle 8.9, Java 17, Android SDK 35; run gradle -p android assembleDebug.
The workflow produces a debug-signed installable APK for testing, not a Play Store release. Each clean build may have a different debug key, requiring uninstall before updating (which erases local demo data).
Runtime testing on a physical Android device remains required.
