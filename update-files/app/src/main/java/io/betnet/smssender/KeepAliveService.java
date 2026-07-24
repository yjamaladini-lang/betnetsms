package io.betnet.smssender;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

public final class KeepAliveService extends Service {
    public static final String CHANNEL_ID = "hormoz_notifier_keep_alive";
    public static final int NOTIFICATION_ID = 9181;
    private static final long HEARTBEAT_MS = 2 * 60 * 1000L;
    private static volatile boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (!AppPrefs.isEnabled(KeepAliveService.this)) {
                stopForeground(true);
                stopSelf();
                return;
            }
            requestListenerRebind(KeepAliveService.this);
            updateForegroundNotification();
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    public static void start(Context context) {
        if (context == null || !AppPrefs.isEnabled(context)) return;
        Intent intent = new Intent(context, KeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
            scheduleRestart(context, 5_000L);
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        try { context.stopService(new Intent(context, KeepAliveService.class)); }
        catch (Exception ignored) {}
    }

    public static boolean isRunning() {
        return running;
    }

    public static void requestListenerRebind(Context context) {
        if (context == null || !hasNotificationAccess(context) || SmsNotificationListener.isConnected()) return;
        try {
            NotificationListenerService.requestRebind(
                    new ComponentName(context, SmsNotificationListener.class)
            );
        } catch (Exception ignored) {}
    }

    public static void scheduleRestart(Context context, long delayMs) {
        if (context == null || !AppPrefs.isEnabled(context)) return;
        try {
            Intent restartIntent = new Intent(context, KeepAliveRestartReceiver.class)
                    .setAction("io.betnet.smssender.RESTART_KEEP_ALIVE");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9182,
                    restartIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                long triggerAt = System.currentTimeMillis() + Math.max(1_500L, delayMs);
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (Exception ignored) {}
    }

    private static boolean hasNotificationAccess(Context context) {
        try {
            String enabled = Settings.Secure.getString(
                    context.getContentResolver(),
                    "enabled_notification_listeners"
            );
            return enabled != null && enabled.contains(context.getPackageName());
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        ensureChannel();
        promoteToForeground();
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, 2_000L);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!AppPrefs.isEnabled(this)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        promoteToForeground();
        requestListenerRebind(this);
        return START_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        requestListenerRebind(this);
        scheduleRestart(this, 2_500L);
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(heartbeat);
        if (AppPrefs.isEnabled(this)) scheduleRestart(this, 4_000L);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private void promoteToForeground() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception first) {
            try { startForeground(NOTIFICATION_ID, notification); }
            catch (Exception ignored) {}
        }
    }

    private void updateForegroundNotification() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (Exception ignored) {}
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                9183,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String status;
        if (!hasNotificationAccess(this)) {
            status = "دسترسی خواندن اعلان‌ها خاموش است؛ برای تنظیم لمس کن";
        } else if (SmsNotificationListener.isConnected()) {
            status = "در حال پایش اعلان‌ها و ارسال خودکار به وب‌هوک";
        } else {
            status = "در حال اتصال مجدد به سرویس اعلان‌ها";
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("نوتیفر هرمز فعال است")
                .setContentText(status)
                .setStyle(new Notification.BigTextStyle().bigText(status))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_LOW)
                .setShowWhen(false);

        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "اجرای دائمی نوتیفر هرمز",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("نگه‌داشتن سرویس خواندن اعلان‌ها و اتصال مجدد خودکار");
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }
}
