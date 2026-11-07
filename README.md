# Kay's NFC History

A free, offline Android app for detecting nearby NFC tags and saving their readable details on your phone.

## What it does

- **Live scanning:** While the app is open, Android's foreground NFC reader mode captures supported NFC tags, with available UID, tag technology, optional NDEF contents and local timestamps.
- **Automatic capture while the app is closed:** Android's tag dispatch system can start a small transparent capture activity for supported tag formats while the phone is **unlocked**, even if you are using another app. Depending on the tag, other installed apps and your Android version, you may be asked which app should handle a tag. The capture handler saves it locally without opening the full scanner UI.
- **History:** Browse/search saved scans, export CSV or delete local records. No account, internet or cloud service required.

## Android hardware and operating-system limitations

**Kay's NFC History cannot continuously read NFC while the phone is locked or the screen is off.** Standard Android NFC tag polling is generally disabled in those conditions. A foreground service cannot bypass this restriction. NFC requires close proximity to the phone's built-in antenna, typically a few centimeters. The app cannot passively inspect NFC transactions or reconstruct encounters that Android never logged.

Android uses its tag dispatch priority rules: other applications or the OS may claim some tags, so automatic capture is best-effort and cannot guarantee reception of every tag.

## Build your APK

In GitHub: **Actions → Build Kay's NFC History APK → latest successful run → Artifacts → Kays-NFC-History-APK**. Download the ZIP, extract **app-debug.apk** and install it on an NFC-capable Android device.

**Update note:** GitHub's standard debug signing key may change between Actions runners. If an update fails with an app-signature mismatch, export your history before uninstalling the previous APK; uninstalling deletes the local database. A persistent private signing keystore should be configured for production/distribution.

Android 8.0+ is supported. APK builds use Java 17 / Android Gradle Plugin 8.7.3 / Gradle 8.9. NFC data stays on the phone.
