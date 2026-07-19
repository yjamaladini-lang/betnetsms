package io.betnet.smssender;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class HistoryDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "history.db";
    private static final int DB_VERSION = 2;

    public HistoryDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT,created_at INTEGER NOT NULL,sender TEXT,message TEXT,package_name TEXT,status TEXT,http_code INTEGER,response TEXT,attempt_count INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE attempts (id INTEGER PRIMARY KEY AUTOINCREMENT,history_id INTEGER NOT NULL,attempt_no INTEGER NOT NULL,created_at INTEGER NOT NULL,status TEXT,http_code INTEGER,response TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE history ADD COLUMN attempt_count INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            db.execSQL("CREATE TABLE IF NOT EXISTS attempts (id INTEGER PRIMARY KEY AUTOINCREMENT,history_id INTEGER NOT NULL,attempt_no INTEGER NOT NULL,created_at INTEGER NOT NULL,status TEXT,http_code INTEGER,response TEXT)");
        }
    }

    public synchronized long insertPending(long createdAt, String sender, String message, String packageName) {
        ContentValues v = new ContentValues();
        v.put("created_at", createdAt); v.put("sender", sender); v.put("message", message);
        v.put("package_name", packageName); v.put("status", "pending"); v.put("http_code", 0);
        v.put("response", "در صف ارسال"); v.put("attempt_count", 0);
        long id = getWritableDatabase().insert("history", null, v); trim(); return id;
    }

    public synchronized void addAttempt(long historyId, int attemptNo, boolean success, int code, String response) {
        ContentValues a = new ContentValues();
        a.put("history_id", historyId); a.put("attempt_no", attemptNo); a.put("created_at", System.currentTimeMillis());
        a.put("status", success ? "sent" : "failed"); a.put("http_code", code); a.put("response", response == null ? "" : response);
        getWritableDatabase().insert("attempts", null, a);
        ContentValues h = new ContentValues();
        h.put("status", success ? "sent" : "retrying"); h.put("http_code", code);
        h.put("response", response == null ? "" : response); h.put("attempt_count", attemptNo);
        getWritableDatabase().update("history", h, "id=?", new String[]{String.valueOf(historyId)});
    }

    public synchronized void markFailed(long historyId, int attempts, int code, String response) {
        ContentValues v = new ContentValues(); v.put("status", "failed"); v.put("attempt_count", attempts);
        v.put("http_code", code); v.put("response", response == null ? "" : response);
        getWritableDatabase().update("history", v, "id=?", new String[]{String.valueOf(historyId)});
    }

    public synchronized HistoryItem get(long id) {
        try (Cursor c = getReadableDatabase().query("history", null, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (c.moveToFirst()) return fromCursor(c);
        }
        return null;
    }

    public synchronized List<HistoryItem> latest(int limit) {
        List<HistoryItem> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("history", null, null, null, null, null, "id DESC", String.valueOf(limit))) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public synchronized List<AttemptItem> attempts(long historyId) {
        List<AttemptItem> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("attempts", null, "history_id=?", new String[]{String.valueOf(historyId)}, null, null, "attempt_no ASC")) {
            while (c.moveToNext()) out.add(new AttemptItem(c.getInt(c.getColumnIndexOrThrow("attempt_no")), c.getLong(c.getColumnIndexOrThrow("created_at")), c.getString(c.getColumnIndexOrThrow("status")), c.getInt(c.getColumnIndexOrThrow("http_code")), c.getString(c.getColumnIndexOrThrow("response"))));
        }
        return out;
    }

    private HistoryItem fromCursor(Cursor c) {
        return new HistoryItem(c.getLong(c.getColumnIndexOrThrow("id")), c.getLong(c.getColumnIndexOrThrow("created_at")), c.getString(c.getColumnIndexOrThrow("sender")), c.getString(c.getColumnIndexOrThrow("message")), c.getString(c.getColumnIndexOrThrow("package_name")), c.getString(c.getColumnIndexOrThrow("status")), c.getInt(c.getColumnIndexOrThrow("http_code")), c.getString(c.getColumnIndexOrThrow("response")), c.getInt(c.getColumnIndexOrThrow("attempt_count")));
    }

    private void trim() { getWritableDatabase().execSQL("DELETE FROM attempts WHERE history_id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 200)"); getWritableDatabase().execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 200)"); }

    public static final class HistoryItem {
        public final long id, createdAt; public final String sender, message, packageName, status, response; public final int httpCode, attemptCount;
        HistoryItem(long id,long createdAt,String sender,String message,String packageName,String status,int httpCode,String response,int attemptCount){this.id=id;this.createdAt=createdAt;this.sender=sender;this.message=message;this.packageName=packageName;this.status=status;this.httpCode=httpCode;this.response=response;this.attemptCount=attemptCount;}
    }
    public static final class AttemptItem {
        public final int attemptNo,httpCode; public final long createdAt; public final String status,response;
        AttemptItem(int attemptNo,long createdAt,String status,int httpCode,String response){this.attemptNo=attemptNo;this.createdAt=createdAt;this.status=status;this.httpCode=httpCode;this.response=response;}
    }
}
