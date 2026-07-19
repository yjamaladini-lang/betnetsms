package io.betnet.smssender;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.Locale;

public final class SmsNotificationListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!AppPrefs.isEnabled(this)) return;

        String webhook = AppPrefs.getWebhook(this);
        if (TextUtils.isEmpty(webhook) || (!webhook.startsWith("https://") && !webhook.startsWith("http://"))) {
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        String sender = firstNonEmpty(
                charSequence(extras.getCharSequence(Notification.EXTRA_TITLE)),
                charSequence(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
                "Unknown"
        );
        String message = firstNonEmpty(
                charSequence(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
                charSequence(extras.getCharSequence(Notification.EXTRA_TEXT)),
                charSequence(extras.getCharSequence(Notification.EXTRA_INFO_TEXT)),
                ""
        );

        if (message.isEmpty()) return;
        if (!matches(sender, AppPrefs.getSenderFilter(this))) return;
        if (!matches(message, AppPrefs.getTextFilter(this))) return;

        long timestamp = System.currentTimeMillis();
        HistoryDb db = new HistoryDb(this);
        long historyId = db.insertPending(timestamp, sender, message, sbn.getPackageName());
        WebhookSender.send(this, webhook, sender, message, sbn.getPackageName(), timestamp, false, historyId, null);
    }

    private static boolean matches(String value, String filter) {
        if (filter == null || filter.trim().isEmpty()) return true;
        String haystack = value == null ? "" : value.toLowerCase(Locale.ROOT);
        String[] tokens = filter.split("[,،\\n]");
        for (String token : tokens) {
            String clean = token.trim().toLowerCase(Locale.ROOT);
            if (!clean.isEmpty() && haystack.contains(clean)) return true;
        }
        return false;
    }

    private static String charSequence(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
}
