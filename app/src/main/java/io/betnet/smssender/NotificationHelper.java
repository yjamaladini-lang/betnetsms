package io.betnet.smssender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

public final class NotificationHelper {
    public static final String CHANNEL_ID = "detected_messages";
    public static final String ACTION_IGNORE_APP = "io.betnet.smssender.IGNORE_APP";
    public static final String EXTRA_PACKAGE = "package_name";
    public static final String EXTRA_HISTORY_ID = "history_id";

    private NotificationHelper() {}

    public static void showDetected(Context context, long historyId, String sender, String message, String packageName) {
        NotificationManager nm = manager(context);
        ensureChannel(nm);
        nm.notify(notificationId(historyId), build(context, historyId, sender, message, packageName,
                "پیام شناسایی شد", "در حال ارسال به وب‌هوک…", false));
    }

    public static void showSuccess(Context context, long historyId, String sender, String message, String packageName, int attempt) {
        NotificationManager nm = manager(context);
        ensureChannel(nm);
        nm.notify(notificationId(historyId), build(context, historyId, sender, message, packageName,
                "ارسال به وب‌هوک موفق بود", "تلاش " + attempt + " با موفقیت انجام شد", true));
    }

    public static void showFailed(Context context, long historyId, String sender, String message, String packageName) {
        NotificationManager nm = manager(context);
        ensureChannel(nm);
        nm.notify(notificationId(historyId), build(context, historyId, sender, message, packageName,
                "ارسال به وب‌هوک ناموفق بود", "برای مشاهده جزئیات یا ارسال مجدد لمس کن", true));
    }

    public static void cancel(Context context, long historyId) {
        manager(context).cancel(notificationId(historyId));
    }

    private static Notification build(Context context, long historyId, String sender, String message,
                                      String packageName, String title, String status, boolean autoCancel) {
        Intent detailIntent = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_HISTORY_ID, historyId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent detailPending = PendingIntent.getActivity(context, notificationId(historyId), detailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent ignoreIntent = new Intent(context, NotificationActionReceiver.class)
                .setAction(ACTION_IGNORE_APP)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_HISTORY_ID, historyId);
        PendingIntent ignorePending = PendingIntent.getBroadcast(context, notificationId(historyId) + 1, ignoreIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(sender + " • " + status)
                .setSubText(getAppLabel(context, packageName))
                .setStyle(new Notification.BigTextStyle().bigText(message).setSummaryText(status))
                .setContentIntent(detailPending)
                .setAutoCancel(autoCancel)
                .setOnlyAlertOnce(false)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(Notification.PRIORITY_HIGH)
                .addAction(new Notification.Action.Builder(null, "نادیده‌گرفتن برنامه", ignorePending).build())
                .addAction(new Notification.Action.Builder(null, "نمایش جزئیات", detailPending).build());

        Bitmap icon = getAppIcon(context, packageName);
        if (icon != null) b.setLargeIcon(icon);
        return b.build();
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static void ensureChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "پیام‌های شناسایی‌شده و وب‌هوک", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("نمایش پیام شناسایی‌شده و نتیجه ارسال به وب‌هوک");
            nm.createNotificationChannel(channel);
        }
    }

    private static int notificationId(long historyId) {
        return (int) (historyId & 0x7fffffff);
    }

    private static String getAppLabel(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private static Bitmap getAppIcon(Context context, String packageName) {
        try {
            Drawable d = context.getPackageManager().getApplicationIcon(packageName);
            if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, d.getIntrinsicWidth()), Math.max(1, d.getIntrinsicHeight()), Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            d.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
}
