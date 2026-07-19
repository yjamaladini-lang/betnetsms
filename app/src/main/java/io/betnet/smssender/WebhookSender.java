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
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onComplete(boolean success, int code, String response);
    }

    private WebhookSender() {}

    public static void send(Context context, String webhook, String sender, String message,
                            String packageName, long timestamp, boolean test, long historyId,
                            Callback callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            int code = 0;
            String response;
            boolean success = false;
            try {
                JSONObject json = new JSONObject();
                json.put("sender", sender);
                json.put("message", message);
                json.put("timestamp", timestamp);
                json.put("package_name", packageName);
                json.put("source", "betnet-sms-sender");
                json.put("test", test);

                byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
                HttpURLConnection connection = (HttpURLConnection) new URL(webhook).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json, text/plain, */*");
                connection.setRequestProperty("User-Agent", "BetnetSmsSender/1.0");
                connection.setFixedLengthStreamingMode(body.length);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }

                code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400
                        ? connection.getInputStream() : connection.getErrorStream();
                response = readStream(stream);
                success = code >= 200 && code < 300;
                connection.disconnect();
            } catch (Exception exception) {
                response = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }

            if (historyId > 0) {
                new HistoryDb(appContext).updateResult(historyId, success, code, response);
            }
            if (callback != null) {
                callback.onComplete(success, code, response);
            }
        });
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && result.length() < 4000) {
                result.append(line).append('\n');
            }
        }
        return result.toString().trim();
    }
}
