package io.betnet.smssender;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebhookSender {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public interface Callback { void onComplete(boolean success, int code, String response); }
    private WebhookSender() {}

    public static void sendWithRetry(Context context, String webhook, String sender, String message,
                                     String packageName, long timestamp, boolean test, long historyId,
                                     int maxAttempts, int delaySeconds, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            Result last = new Result(false, 0, "ارسال انجام نشد");
            int attempts = Math.max(1, maxAttempts);
            for (int i = 1; i <= attempts; i++) {
                last = sendOnce(webhook, sender, message, packageName, timestamp, test, i);
                new HistoryDb(app).addAttempt(historyId, i, last.success, last.code, last.response);
                if (last.success) {
                    NotificationHelper.showSuccess(app, historyId, sender, message, packageName, i);
                    if (callback != null) callback.onComplete(true, last.code, last.response);
                    return;
                }
                if (i < attempts) {
                    try { Thread.sleep(Math.max(1, delaySeconds) * 1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }
            new HistoryDb(app).markFailed(historyId, attempts, last.code, last.response);
            NotificationHelper.showFailed(app, historyId, sender, message, packageName);
            if (callback != null) callback.onComplete(false, last.code, last.response);
        });
    }

    private static Result sendOnce(String webhook,String sender,String message,String packageName,long timestamp,boolean test,int attempt) {
        int code = 0; String response; boolean success = false;
        try {
            JSONObject json = new JSONObject();
            json.put("sender", sender); json.put("message", message); json.put("timestamp", timestamp);
            json.put("package_name", packageName); json.put("source", "betnet-sms-sender");
            json.put("test", test); json.put("attempt", attempt);
            byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection c = (HttpURLConnection) new URL(webhook).openConnection();
            c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("User-Agent", "HormozNotifier/1.8.2"); c.setFixedLengthStreamingMode(body.length);
            try (OutputStream o = c.getOutputStream()) { o.write(body); }
            code = c.getResponseCode(); InputStream s = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
            response = readStream(s); success = code >= 200 && code < 300; c.disconnect();
        } catch (Exception e) { response = e.getClass().getSimpleName() + ": " + e.getMessage(); }
        return new Result(success, code, response);
    }

    private static String readStream(InputStream stream) throws Exception { if (stream == null) return ""; StringBuilder r = new StringBuilder(); try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) { String line; while ((line = br.readLine()) != null && r.length() < 4000) r.append(line).append('\n'); } return r.toString().trim(); }
    private static final class Result { final boolean success; final int code; final String response; Result(boolean s,int c,String r){success=s;code=c;response=r;} }
}
