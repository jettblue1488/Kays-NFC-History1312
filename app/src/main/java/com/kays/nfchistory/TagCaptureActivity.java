package com.kays.nfchistory;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * NFC tag dispatch entrypoint used when MainActivity is not in the foreground.
 *
 * Android starts this lightweight transparent activity only if the OS routes
 * a discovered tag to this app. Reading is attempted off the UI thread.
 * No wakelock, polling service or network permission is required.
 *
 * Screen-off or locked NFC polling is controlled by the phone/OS, not this app.
 */
public final class TagCaptureActivity extends Activity {
    private static final long DEDUPLICATE_MS = 1300;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        if (intent == null || !isNfcAction(intent.getAction())) {
            finish();
            return;
        }
        Tag tag;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
            } else {
                tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            }
        } catch (RuntimeException e) {
            finish();
            return;
        }
        if (tag == null) {
            finish();
            return;
        }

        // The tag is often no longer readable a few moments after discovery,
        // so consume its ID and technology list immediately.
        final String uid = TagDecoder.hex(tag.getId());
        final String technologies = TagDecoder.technologies(tag);
        final long detectedAt = System.currentTimeMillis();

        new Thread(() -> {
            ScanDb db = new ScanDb(getApplicationContext());
            try {
                // Avoid repeated entries caused by a tag being held against
                // the phone and triggering NFC dispatch multiple times.
                // Uses app-private preferences so it survives activity restarts.
                synchronized (TagCaptureActivity.class) {
                    android.content.SharedPreferences prefs =
                            getSharedPreferences("nfc_auto_capture", MODE_PRIVATE);
                    String previous = prefs.getString("last_uid", "");
                    long previousAt = prefs.getLong("last_at", 0);
                    if (uid.equals(previous) && detectedAt - previousAt < DEDUPLICATE_MS) {
                        runOnUiThread(this::finish);
                        return;
                    }
                    prefs.edit().putString("last_uid", uid)
                            .putLong("last_at", detectedAt).apply();
                }

                String data = TagDecoder.readNdef(tag);
                db.add(detectedAt, uid, technologies, data);
            } catch (Exception ignored) {
                // Unreadable tags should not crash the NFC dispatch handler.
            } finally {
                db.close();
                runOnUiThread(this::finish);
            }
        }, "nfc-tag-capture").start();
    }

    private static boolean isNfcAction(String action) {
        return NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action);
    }
}
