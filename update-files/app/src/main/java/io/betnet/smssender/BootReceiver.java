package io.betnet.smssender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AppPrefs.isEnabled(context)) return;
        KeepAliveService.start(context);
        KeepAliveService.requestListenerRebind(context);
    }
}
