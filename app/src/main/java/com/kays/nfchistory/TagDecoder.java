package com.kays.nfchistory;

import android.nfc.Tag;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.tech.Ndef;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

final class TagDecoder {
    static String hex(byte[] b) {
        if (b == null || b.length == 0) return "Unavailable";
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte v : b) s.append(String.format(Locale.ROOT, "%02X", v & 0xff));
        return s.toString();
    }

    static String technologies(Tag tag) {
        StringBuilder out = new StringBuilder();
        for (String t : tag.getTechList()) {
            if (out.length() > 0) out.append(", ");
            int p = t.lastIndexOf('.');
            out.append(p < 0 ? t : t.substring(p + 1));
        }
        return out.toString();
    }

    static String readNdef(Tag tag) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) return "No readable NDEF record advertised (tag may use a different protocol).";
        StringBuilder out = new StringBuilder();
        try {
            ndef.connect();
            NdefMessage message = ndef.getNdefMessage();
            if (message == null) return "NDEF tag is empty.";
            for (NdefRecord r : message.getRecords()) {
                if (out.length() > 0) out.append("\n\n");
                out.append(describe(r));
            }
        } catch (Exception ex) {
            return "Tag detected. NDEF read unavailable: " + ex.getClass().getSimpleName();
        } finally {
            try { ndef.close(); } catch (Exception ignored) { }
        }
        return out.length() == 0 ? "No NDEF records." : out.toString();
    }

    private static String describe(NdefRecord r) {
        try {
            if (r.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(r.getType(), NdefRecord.RTD_TEXT)) {
                byte[] payload = r.getPayload();
                if (payload.length == 0) return "Text: (empty)";
                int codeLen = payload[0] & 0x3f;
                int pos = 1 + codeLen;
                if (pos > payload.length) return "Malformed text record";
                String charset = (payload[0] & 0x80) != 0 ? "UTF-16" : "UTF-8";
                return "Text: " + new String(payload, pos, payload.length - pos, java.nio.charset.Charset.forName(charset));
            }
            if (r.toUri() != null) return "URI (not opened): " + r.toUri();
            if (r.getTnf() == NdefRecord.TNF_MIME_MEDIA) {
                return "MIME: " + new String(r.getType(), StandardCharsets.US_ASCII) + "\nPayload hex: " + hexLimited(r.getPayload());
            }
            return "Record type: " + hex(r.getType()) + "\nPayload hex: " + hexLimited(r.getPayload());
        } catch (Exception ex) {
            return "Unreadable record: " + ex.getClass().getSimpleName();
        }
    }

    private static String hexLimited(byte[] data) {
        if (data == null || data.length == 0) return "(empty)";
        if (data.length <= 128) return hex(data);
        return hex(Arrays.copyOf(data, 128)) + "… (" + data.length + " bytes total; display truncated)";
    }
}
