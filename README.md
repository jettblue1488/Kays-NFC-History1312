# Kay's NFC History

Local-only Android NFC tag scanner. The app records NFC tag detections while open, including timestamp, available tag ID and NFC technologies, and readable NDEF content. Features local history, search and CSV export.

NFC detection range is limited by the phone's hardware, generally a few centimeters; the app cannot increase radio range or reconstruct past encounters that were never logged.

## Build APK

Open **Actions → Build Kay's NFC History APK** and run the workflow. After a successful run, download the **Kays-NFC-History-APK** artifact, unzip it and install `app-debug.apk` on an NFC-capable Android phone. This debug APK is for personal testing, not a Play Store release.

No internet permission, login or cloud account is needed.
