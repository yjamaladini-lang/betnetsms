package io.betnet.smssender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public final class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!NotificationHelper.ACTION_IGNORE_APP.equals(intent.getAction())) return;
        String packageName = intent.getStringExtra(NotificationHelper.EXTRA_PACKAGE);
        long historyId = intent.getLongExtra(NotificationHelper.EXTRA_HISTORY_ID, -1L);
        if (packageName != null && !packageName.isEmpty()) {
            AppPrefs.removeAllowedPackage(context, packageName);
            Toast.makeText(context, "برنامه از فهرست مجاز حذف شد.", Toast.LENGTH_SHORT).show();
        }
        if (historyId > 0) NotificationHelper.cancel(context, historyId);
    }
}
