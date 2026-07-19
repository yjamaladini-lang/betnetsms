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
    private static final int DB_VERSION = 1;

    public HistoryDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "created_at INTEGER NOT NULL," +
                "sender TEXT," +
                "message TEXT," +
                "package_name TEXT," +
                "status TEXT," +
                "http_code INTEGER," +
                "response TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS history");
        onCreate(db);
    }

    public synchronized long insertPending(long createdAt, String sender, String message, String packageName) {
        ContentValues values = new ContentValues();
        values.put("created_at", createdAt);
        values.put("sender", sender);
        values.put("message", message);
        values.put("package_name", packageName);
        values.put("status", "pending");
        values.put("http_code", 0);
        values.put("response", "در صف ارسال");
        long id = getWritableDatabase().insert("history", null, values);
        trim();
        return id;
    }

    public synchronized void updateResult(long id, boolean success, int code, String response) {
        ContentValues values = new ContentValues();
        values.put("status", success ? "sent" : "failed");
        values.put("http_code", code);
        values.put("response", response == null ? "" : response);
        getWritableDatabase().update("history", values, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized List<HistoryItem> latest(int limit) {
        List<HistoryItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "history", null, null, null, null, null,
                "id DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) {
                items.add(new HistoryItem(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        cursor.getString(cursor.getColumnIndexOrThrow("sender")),
                        cursor.getString(cursor.getColumnIndexOrThrow("message")),
                        cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("status")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("http_code")),
                        cursor.getString(cursor.getColumnIndexOrThrow("response"))
                ));
            }
        }
        return items;
    }

    private void trim() {
        getWritableDatabase().execSQL(
                "DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 200)"
        );
    }

    public static final class HistoryItem {
        public final long id;
        public final long createdAt;
        public final String sender;
        public final String message;
        public final String packageName;
        public final String status;
        public final int httpCode;
        public final String response;

        HistoryItem(long id, long createdAt, String sender, String message, String packageName,
                    String status, int httpCode, String response) {
            this.id = id;
            this.createdAt = createdAt;
            this.sender = sender;
            this.message = message;
            this.packageName = packageName;
            this.status = status;
            this.httpCode = httpCode;
            this.response = response;
        }
    }
}
