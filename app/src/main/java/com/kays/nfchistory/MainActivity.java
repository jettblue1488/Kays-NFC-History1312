package com.kays.nfchistory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private static final int EXPORT_REQUEST = 502;
    private final int ink = Color.rgb(22, 53, 69);
    private final int sea = Color.rgb(6, 126, 140);
    private final int light = Color.rgb(242, 248, 249);
    private final AtomicLong lastScanTime = new AtomicLong(0);
    private volatile String lastScanUid = "";
    private NfcAdapter adapter;
    private ScanDb database;
    private ToneGenerator tone;
    private TextView status, countLabel;
    private EditText filter;
    private LinearLayout logList;
    private Button scanButton;
    private boolean scanWanted = true;
    private boolean readerActive = false;
    private boolean inForeground = false;
    private DateFormat clock;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = NfcAdapter.getDefaultAdapter(this);
        database = new ScanDb(this);
        clock = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        try { tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70); } catch (RuntimeException ignored) { }
        buildUi();
        refreshHistory();
    }

    private void buildUi() {
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(light);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(22), dp(16), dp(28));
        scroller.addView(content);
        if (Build.VERSION.SDK_INT >= 30) {
            scroller.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets s = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(s.left, s.top, s.right, s.bottom);
                return insets;
            });
        }
        setContentView(scroller);

        TextView title = text("Kay's NFC History", 26, ink, true);
        content.addView(title);
        TextView subtitle = text("Offline • Private • Saved on this device", 13, sea, false);
        content.addView(subtitle, margin(0, 2, 0, 16));

        LinearLayout panel = panel();
        content.addView(panel);
        status = text("Checking NFC…", 16, ink, true);
        panel.addView(status);
        scanButton = button("Pause live scanning", sea);
        scanButton.setOnClickListener(v -> {
            scanWanted = !scanWanted;
            updateReaderMode();
        });
        panel.addView(scanButton, margin(0, 12, 0, 5));
        Button settings = button("Open NFC settings", ink);
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)); }
            catch (Exception ex) { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
        });
        panel.addView(settings);
        Button copy = button("Read, Copy & Write NFC tags", sea);
        copy.setOnClickListener(v -> startActivity(new Intent(this, NdefCopyActivity.class)));
        panel.addView(copy, margin(0, 8, 0, 0));
        panel.addView(text("Live scan works while this screen is open. Automatic capture can save supported tags while you use another app, provided the phone is unlocked and Android routes the tag to Kay's NFC History.", 13, ink, false), margin(0, 12, 0, 0));

        LinearLayout information = panel();
        content.addView(information, margin(0, 12, 0, 0));
        information.addView(text("Range & detection", 17, ink, true));
        information.addView(text("NFC range is limited by the phone's built-in antenna, usually just centimeters. This app uses Android's dedicated foreground reader mode, but software cannot boost NFC radio power or detect tags across a room.", 13, ink, false), margin(0, 6, 0, 0));
        information.addView(text("Screen off / locked: Android usually stops ordinary NFC tag polling. This app cannot override that OS restriction. Past unlogged encounters cannot be recovered, and it cannot passively monitor other phones or payment transactions.", 12, sea, false), margin(0, 8, 0, 0));

        countLabel = text("History", 18, ink, true);
        content.addView(countLabel, margin(0, 22, 0, 6));
        filter = new EditText(this);
        filter.setSingleLine(true);
        filter.setTextSize(15);
        filter.setHint("Search UID, tag type, or data");
        filter.setBackground(drawable(Color.WHITE, 12, Color.rgb(207, 221, 225)));
        filter.setPadding(dp(14), dp(8), dp(14), dp(8));
        content.addView(filter, margin(0, 6, 0, 12));
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshHistory(); }
            @Override public void afterTextChanged(Editable e) { }
        });

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        Button export = button("Export CSV", sea);
        export.setOnClickListener(v -> exportHistory());
        toolbar.addView(export, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button clear = button("Clear history", ink);
        clear.setOnClickListener(v -> confirmClear());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        clearParams.setMargins(dp(8), 0, 0, 0);
        toolbar.addView(clear, clearParams);
        content.addView(toolbar);

        logList = new LinearLayout(this);
        logList.setOrientation(LinearLayout.VERTICAL);
        content.addView(logList, margin(0, 12, 0, 0));
        content.addView(text("Each row is a detected tag event. Tap a row for complete locally saved information.", 12, sea, false), margin(0, 12, 0, 0));
    }

    @Override public void onResume() {
        super.onResume();
        inForeground = true;
        updateReaderMode();
    }

    @Override public void onPause() {
        inForeground = false;
        if (readerActive && adapter != null) {
            try { adapter.disableReaderMode(this); } catch (Exception ignored) { }
            readerActive = false;
        }
        super.onPause();
    }

    private void updateReaderMode() {
        if (adapter == null) {
            status.setText("NFC hardware is not available on this device.");
            scanButton.setEnabled(false);
            scanButton.setText("NFC not supported");
            return;
        }
        if (!adapter.isEnabled()) {
            if (readerActive) {
                try { adapter.disableReaderMode(this); } catch (Exception ignored) { }
                readerActive = false;
            }
            status.setText("NFC is off — enable it in Settings.");
            scanButton.setText("Start live scanning");
            return;
        }
        if (readerActive && (!scanWanted || !inForeground)) {
            try { adapter.disableReaderMode(this); } catch (Exception ignored) { }
            readerActive = false;
        }
        if (scanWanted && inForeground && !readerActive) {
            Bundle opts = new Bundle();
            // Faster repeat presence checks, not increased radio range.
            opts.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 100);
            int flags = NfcAdapter.FLAG_READER_NFC_A
                    | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F
                    | NfcAdapter.FLAG_READER_NFC_V;
            try {
                adapter.enableReaderMode(this, this, flags, opts);
                readerActive = true;
            } catch (Exception ex) {
                status.setText("Could not start reader mode: " + ex.getClass().getSimpleName());
            }
        }
        if (readerActive) status.setText("● NFC scanner active — bring a tag close");
        else if (!scanWanted) status.setText("Scanning paused");
        scanButton.setText(scanWanted ? "Pause live scanning" : "Start live scanning");
    }

    @Override public void onTagDiscovered(Tag tag) {
        long now = System.currentTimeMillis();
        String uid = TagDecoder.hex(tag.getId());
        // Suppress rapid duplicate callbacks from a single continuous tag presentation.
        synchronized (this) {
            if (uid.equals(lastScanUid) && now - lastScanTime.get() < 1300) return;
            lastScanUid = uid;
            lastScanTime.set(now);
        }
        String tech = TagDecoder.technologies(tag);
        String ndef = TagDecoder.readNdef(tag);
        try {
            database.add(now, uid, tech, ndef);
            int times = database.countForUid(uid);
            runOnUiThread(() -> {
                if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 130);
                refreshHistory();
                String message = "Saved tag " + (uid.length() > 14 ? uid.substring(0, 14) + "…" : uid);
                if (times > 1) message += " (seen " + times + " times)";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            });
        } catch (Exception ex) {
            runOnUiThread(() -> Toast.makeText(this, "Could not save scan", Toast.LENGTH_LONG).show());
        }
    }

    private void refreshHistory() {
        if (logList == null || countLabel == null) return;
        String q = filter == null ? "" : filter.getText().toString();
        List<ScanDb.Event> entries = database.all(q);
        countLabel.setText("History • " + database.total() + " saved scans");
        logList.removeAllViews();
        if (entries.isEmpty()) {
            logList.addView(text(q.isEmpty() ? "No scans yet. Hold a compatible NFC tag against your phone while this screen is open." : "No matching scans.", 14, ink, false));
            return;
        }
        for (ScanDb.Event e : entries) {
            LinearLayout card = panel();
            String main = e.uid.equals("Unavailable") ? "UID unavailable" : e.uid;
            card.addView(text(main, 16, ink, true));
            card.addView(text(clock.format(new Date(e.at)), 13, sea, false), margin(0, 3, 0, 3));
            card.addView(text(e.tech.length() > 0 ? e.tech : "Unknown tag technology", 12, ink, false));
            if (!e.data.isEmpty()) {
                String preview = e.data.length() > 110 ? e.data.substring(0, 110) + "…" : e.data;
                card.addView(text(preview, 12, ink, false), margin(0, 6, 0, 0));
            }
            card.setOnClickListener(v -> showEvent(e));
            logList.addView(card, margin(0, 0, 0, 9));
        }
    }

    private void showEvent(ScanDb.Event event) {
        String info = "Detected: " + clock.format(new Date(event.at))
                + "\n\nTag UID: " + event.uid
                + "\n\nTechnologies: " + event.tech
                + "\n\nData:\n" + event.data;
        new AlertDialog.Builder(this)
                .setTitle("NFC scan details")
                .setMessage(info)
                .setPositiveButton("Close", (d, w) -> { })
                .setNeutralButton("Copy details", (d, w) -> {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("NFC scan", info));
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Delete saved NFC scans?")
                .setMessage("This permanently deletes the local scan history on this device. There is no cloud backup.")
                .setNegativeButton("Cancel", (d, w) -> { })
                .setPositiveButton("Delete", (d, w) -> {
                    database.clear();
                    refreshHistory();
                }).show();
    }

    private void exportHistory() {
        if (database.total() == 0) {
            Toast.makeText(this, "No scans to export yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "Kays-NFC-History.csv");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST || resultCode != RESULT_OK || data == null) return;
        Uri target = data.getData();
        if (target == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(target)) {
            if (out == null) throw new IllegalStateException("Cannot open destination");
            out.write("Date/time,Unix milliseconds,Tag UID,Technologies,Tag data\r\n".getBytes(StandardCharsets.UTF_8));
            for (ScanDb.Event e : database.all("")) {
                String line = csv(clock.format(new Date(e.at))) + "," + e.at + ","
                        + csv(e.uid) + "," + csv(e.tech) + "," + csv(e.data) + "\r\n";
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "CSV saved", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String csv(String s) {
        // Prevent spreadsheet formula injection from untrusted tag records.
        String v = s == null ? "" : s;
        String lead = v.trim();
        if (!lead.isEmpty() && "=+-@".indexOf(lead.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(15), dp(14), dp(15), dp(14));
        p.setBackground(drawable(Color.WHITE, 14, Color.rgb(220, 232, 234)));
        p.setElevation(dp(1));
        return p;
    }

    private GradientDrawable drawable(int background, int radius, int border) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(background);
        gd.setCornerRadius(dp(radius));
        gd.setStroke(dp(1), border);
        return gd;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(2), 1f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(drawable(color, 10, color));
        return b;
    }

    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    @Override public void onDestroy() {
        if (tone != null) tone.release();
        database.close();
        super.onDestroy();
    }
}
