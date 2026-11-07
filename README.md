# Kay's NFC History 2 (Android)

This release fixes the **App not installed as package conflicts with an existing package** error. It uses a new application ID so the new version can install beside the old Kay's NFC History app, even when the original APK has a different signature.

## Download

**[Get Kay's NFC History 2 APK](https://github.com/jettblue1488/Kays-NFC-History1312/releases/download/v1.2.0/app-debug.apk)**

Tap the link on the Android phone, open the downloaded APK and choose Install. It appears in the launcher as **Kay's NFC History 2**. You can keep the old app installed; do **not** uninstall it if it has saved scans you need.

**Important:** The old app's stored scans do not automatically migrate into this version, since Android isolates app storage between application IDs. You can export the old history to a CSV file before changing apps. Signing-key caching is for convenience in development; do not rely on it as a permanent release signing solution.

## Features

- On-screen live NFC tag reader for compatible nearby tags.
- Best-effort automatic tag dispatch when using another app and the phone is **unlocked**; Android may open a chooser or route the tag elsewhere.
- Timestamped local NFC history, optional NDEF tag data, search and CSV export.
- No network permissions or sign-in.

## NFC limitations

Normal Android apps cannot continuously poll NFC tags when the screen is off or the device is locked. The phone's antenna limits detection to close range, usually a few centimeters. The app cannot reconstruct old, unlogged NFC encounters.

## Source / builds

The Android app is built through GitHub Actions under **Actions → Build and publish Kay's NFC History APK**, and the installable file appears under Releases as `app-debug.apk`. Android Gradle Plugin 8.7.3 / Gradle 8.9 / JDK 17. This is a personal-use debug APK.
