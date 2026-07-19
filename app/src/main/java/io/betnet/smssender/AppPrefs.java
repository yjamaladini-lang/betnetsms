package io.betnet.smssender;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AppPrefs {
    private static final String FILE = "betnet_sms_sender";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_WEBHOOK = "webhook";
    private static final String KEY_SENDER_FILTER = "sender_filter";
    private static final String KEY_TEXT_FILTER = "text_filter";
    private static final String KEY_ALLOWED_PACKAGES = "allowed_packages";
    private static final String KEY_RETRY_COUNT = "retry_count";
    private static final String KEY_RETRY_SECONDS = "retry_seconds";

    private AppPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) { return prefs(context).getBoolean(KEY_ENABLED, true); }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String getWebhook(Context context) { return prefs(context).getString(KEY_WEBHOOK, ""); }
    public static String getSenderFilter(Context context) { return prefs(context).getString(KEY_SENDER_FILTER, ""); }
    public static String getTextFilter(Context context) { return prefs(context).getString(KEY_TEXT_FILTER, ""); }
    public static int getRetryCount(Context context) { return Math.max(1, prefs(context).getInt(KEY_RETRY_COUNT, 5)); }
    public static int getRetrySeconds(Context context) { return Math.max(1, prefs(context).getInt(KEY_RETRY_SECONDS, 5)); }

    public static Set<String> getAllowedPackages(Context context) {
        String raw = prefs(context).getString(KEY_ALLOWED_PACKAGES, "");
        if (raw == null || raw.trim().isEmpty()) return Collections.emptySet();
        return new HashSet<>(Arrays.asList(raw.split("\\|")));
    }

    public static boolean isPackageAllowed(Context context, String packageName) {
        Set<String> packages = getAllowedPackages(context);
        return !packages.isEmpty() && packages.contains(packageName);
    }


    public static void removeAllowedPackage(Context context, String packageName) {
        Set<String> packages = new HashSet<>(getAllowedPackages(context));
        packages.remove(packageName);
        prefs(context).edit().putString(KEY_ALLOWED_PACKAGES, String.join("|", packages)).apply();
    }

    public static void save(Context context, boolean enabled, String webhook, String senderFilter,
                            String textFilter, Set<String> allowedPackages, int retryCount, int retrySeconds) {
        String packageValue = allowedPackages == null ? "" : String.join("|", allowedPackages);
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_WEBHOOK, webhook.trim())
                .putString(KEY_SENDER_FILTER, senderFilter.trim())
                .putString(KEY_TEXT_FILTER, textFilter.trim())
                .putString(KEY_ALLOWED_PACKAGES, packageValue)
                .putInt(KEY_RETRY_COUNT, Math.max(1, retryCount))
                .putInt(KEY_RETRY_SECONDS, Math.max(1, retrySeconds))
                .apply();
    }
}
