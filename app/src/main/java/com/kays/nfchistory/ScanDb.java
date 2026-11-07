package com.kays.nfchistory;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import java.util.ArrayList;
import java.util.List;

final class ScanDb extends SQLiteOpenHelper {
    static final class Event {
        long id, at;
        String uid, tech, data;
        Event(long id, long at, String uid, String tech, String data) {
            this.id = id; this.at = at; this.uid = uid; this.tech = tech; this.data = data;
        }
    }

    ScanDb(Context context) { super(context, "local_nfc_history.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE scans (_id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, uid TEXT NOT NULL, tech TEXT NOT NULL, data TEXT NOT NULL)");
        db.execSQL("CREATE INDEX scans_at_idx ON scans(at)");
        db.execSQL("CREATE INDEX scans_uid_idx ON scans(uid)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    synchronized void add(long at, String uid, String tech, String data) {
        ContentValues c = new ContentValues();
        c.put("at", at); c.put("uid", uid); c.put("tech", tech); c.put("data", data);
        getWritableDatabase().insertOrThrow("scans", null, c);
    }

    synchronized List<Event> all(String search) {
        List<Event> result = new ArrayList<>();
        String q = search == null ? "" : search.trim();
        String where = q.isEmpty() ? null : "(uid LIKE ? ESCAPE '\\' OR tech LIKE ? ESCAPE '\\' OR data LIKE ? ESCAPE '\\')";
        String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String v = "%" + escaped + "%";
        String[] args = q.isEmpty() ? null : new String[]{v, v, v};
        try (Cursor c = getReadableDatabase().query("scans", new String[]{"_id", "at", "uid", "tech", "data"}, where, args, null, null, "at DESC, _id DESC", "3000")) {
            while (c.moveToNext()) result.add(new Event(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4)));
        }
        return result;
    }

    synchronized int countForUid(String uid) {
        if (uid.equals("Unavailable")) return 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM scans WHERE uid=?", new String[]{uid})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    synchronized int total() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM scans", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    synchronized void clear() { getWritableDatabase().delete("scans", null, null); }
}
