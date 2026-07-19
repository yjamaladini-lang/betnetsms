package io.betnet.smssender;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AppPrefs.isEnabled(context)) return;
        try {
            NotificationListenerService.requestRebind(
                    new ComponentName(context, SmsNotificationListener.class));
        } catch (Exception ignored) {}
    }
}
