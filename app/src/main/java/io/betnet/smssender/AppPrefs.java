package io.betnet.smssender;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String FILE = "betnet_sms_sender";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_WEBHOOK = "webhook";
    private static final String KEY_SENDER_FILTER = "sender_filter";
    private static final String KEY_TEXT_FILTER = "text_filter";

    private AppPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static String getWebhook(Context context) {
        return prefs(context).getString(KEY_WEBHOOK, "");
    }

    public static String getSenderFilter(Context context) {
        return prefs(context).getString(KEY_SENDER_FILTER, "");
    }

    public static String getTextFilter(Context context) {
        return prefs(context).getString(KEY_TEXT_FILTER, "");
    }

    public static void save(Context context, boolean enabled, String webhook, String senderFilter, String textFilter) {
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_WEBHOOK, webhook.trim())
                .putString(KEY_SENDER_FILTER, senderFilter.trim())
                .putString(KEY_TEXT_FILTER, textFilter.trim())
                .apply();
    }
}
