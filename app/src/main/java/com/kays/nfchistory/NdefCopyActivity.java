package com.kays.nfchistory;

import android.app.Activity;
import android.app.AlertDialog;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

/** Copies standard public NDEF messages, never secure card credentials or tag UIDs. */
public final class NdefCopyActivity extends Activity implements NfcAdapter.ReaderCallback {
    private NfcAdapter adapter;
    private TextView status;
    private Button writeButton;
    private volatile NdefMessage copied;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean writeMode = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = NfcAdapter.getDefaultAdapter(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 70, 32, 30);
        root.setBackgroundColor(Color.rgb(243, 248, 249));
        TextView title = new TextView(this);
        title.setText("NFC Read, Copy & Write");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(22, 53, 69));
        root.addView(title);
        status = new TextView(this);
        status.setTextSize(17);
        status.setPadding(0, 30, 0, 30);
        status.setText("Hold a standard NFC tag against the phone to copy its readable NDEF records.");
        root.addView(status);
        writeButton = new Button(this);
        writeButton.setText("Write copied data to another NFC tag");
        writeButton.setEnabled(false);
        writeButton.setOnClickListener(v -> {
            if (copied == null) return;
            new AlertDialog.Builder(this)
                    .setTitle("Confirm NFC write")
                    .setMessage("Writing can overwrite existing NDEF data on the destination tag. Only use a blank or reusable tag you own. This does not copy a tag's UID, payment credentials, access keys, or protected data. Continue?")
                    .setNegativeButton("Cancel", (d,w) -> {})
                    .setPositiveButton("Continue", (d,w) -> {
                        writeMode = true;
                        status.setText("Write armed. Hold your destination NFC tag near the phone.");
                    }).show();
        });
        root.addView(writeButton);
        Button cancel = new Button(this);
        cancel.setText("Cancel writing / Scan another source");
        cancel.setOnClickListener(v -> {
            writeMode = false;
            status.setText("Read mode. Hold a source NFC tag near the phone.");
        });
        root.addView(cancel);
        TextView note = new TextView(this);
        note.setText("Only ordinary NDEF messages can be copied. Payment, ID, hotel and transit credentials cannot be cloned. Destination tags must support NDEF and have enough writable memory.");
        note.setPadding(0, 30, 0, 0);
        root.addView(note);
        setContentView(root);
    }

    @Override public void onResume() {
        super.onResume();
        if (adapter != null && adapter.isEnabled()) {
            adapter.enableReaderMode(this, this,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B |
                NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V,
                null);
        } else {
            status.setText("Enable NFC in Android Settings before scanning.");
        }
    }
    @Override public void onPause() {
        if (adapter != null) {
            try { adapter.disableReaderMode(this); } catch (Exception ignored) {}
        }
        super.onPause();
    }
    @Override public void onTagDiscovered(Tag tag) {
        if (!busy.compareAndSet(false,true)) return;
        try {
            if (writeMode) write(tag);
            else read(tag);
        } finally { busy.set(false); }
    }
    private void read(Tag tag) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) { message("Tag detected, but no standard NDEF data is available. Protected card contents cannot be copied."); return; }
        try {
            ndef.connect();
            NdefMessage data = ndef.getNdefMessage();
            if (data == null || data.getRecords().length == 0) {
                message("Tag has no NDEF records to copy.");
                return;
            }
            // Only copy public NDEF records, never APDU/payment/access credentials.
            copied = data;
            runOnUiThread(() -> {
                status.setText("Copied " + data.getRecords().length + " NDEF record(s), " + data.toByteArray().length + " bytes. Ready to write.");
                writeButton.setEnabled(true);
            });
        } catch (Exception ex) { message("Could not read NDEF: " + ex.getClass().getSimpleName()); }
        finally { try { ndef.close(); } catch (Exception ignored) {} }
    }
    private void write(Tag tag) {
        NdefMessage payload = copied;
        if (payload == null) { message("Scan a source tag first."); return; }
        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                if (!ndef.isWritable()) { message("Destination tag is read-only."); return; }
                if (payload.toByteArray().length > ndef.getMaxSize()) { message("Destination tag has insufficient capacity."); return; }
                ndef.writeNdefMessage(payload);
                writeMode = false;
                message("NDEF data written successfully. The original tag UID and protected credentials were not copied.");
            } catch (Exception ex) { message("Write failed: " + ex.getClass().getSimpleName()); }
            finally { try { ndef.close(); } catch (Exception ignored) {} }
            return;
        }
        NdefFormatable formatable = NdefFormatable.get(tag);
        if (formatable == null) { message("Destination does not support NDEF writing."); return; }
        try {
            formatable.connect();
            formatable.format(payload);
            writeMode = false;
            message("Destination formatted and NDEF data written successfully.");
        } catch (Exception ex) { message("Formatting/writing failed: " + ex.getClass().getSimpleName()); }
        finally { try { formatable.close(); } catch (Exception ignored) {} }
    }
    private void message(String text) { runOnUiThread(() -> status.setText(text)); }
}
