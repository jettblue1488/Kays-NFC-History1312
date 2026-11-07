# Kay's NFC History

A free, offline Android app for detecting nearby NFC tags and saving their readable details on your phone.

## Download the Android app

**[Download the APK (Android installer)](https://github.com/jettblue1488/Kays-NFC-History1312/releases/latest/download/Kays-NFC-History.apk)**

[View GitHub Releases](https://github.com/jettblue1488/Kays-NFC-History1312/releases)

Tap the APK link on an Android phone, then open the downloaded file in Downloads/Files and allow installation from your browser if prompted. Enable NFC in Android Settings.

This is a debug-signed personal-use APK. If Android rejects an update due to a signature mismatch, export any saved history before uninstalling the old app: uninstalling deletes its local history.

## Features

- **Live NFC scanning:** While the app is open, supported NFC tags can be read and saved with UID, tag technology, NDEF content (when readable), and time.
- **Automatic capture while unlocked:** Android may send tag events to a transparent capture activity even if you are using another app; other apps or the OS can claim some tags. It is not continuous polling.
- **Local history:** Search saved scans, export CSV, and delete local scans. No internet permission or account required.

## Limitations

**Locked/screen-off NFC:** Standard Android NFC polling is generally disabled while the phone is locked or the screen is off. A normal third-party app cannot override this limitation. NFC range is limited to centimeters by hardware. The app cannot recover previously unlogged scans or passively monitor NFC traffic from other devices.

## For developers

Open **Actions → Build and publish Kay's NFC History APK → latest successful run → Artifacts**, download the ZIP, and extract `app-debug.apk`. Gradle 8.9, Android Gradle Plugin 8.7.3 and Java 17 are used. The public release file is automatically uploaded on the next successful main-branch build. A production-quality app update system needs a persistent private signing key and release configuration, which this development workflow does not provide.
