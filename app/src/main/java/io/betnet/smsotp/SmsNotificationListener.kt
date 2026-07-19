package io.betnet.smsotp

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SmsNotificationListener : NotificationListenerService() {
  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null || sbn.packageName == packageName) return
    val settings = Store.loadSettings(this)
    if (!settings.enabled || settings.webhookUrl.isBlank()) return

    val n = sbn.notification ?: return
    if ((n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

    val extras = n.extras
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    val parts = linkedSetOf<String>()
    listOf(
      Notification.EXTRA_TEXT,
      Notification.EXTRA_BIG_TEXT,
      Notification.EXTRA_SUB_TEXT,
      Notification.EXTRA_INFO_TEXT,
      Notification.EXTRA_SUMMARY_TEXT
    ).forEach { key -> extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add) }
    extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line -> line?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add) }
    val message = parts.joinToString("\n").trim()
    if (message.isBlank()) return

    val senderNeedle = settings.senderFilter.trim()
    if (senderNeedle.isNotBlank() && !title.contains(senderNeedle, ignoreCase = true) && !sbn.packageName.contains(senderNeedle, ignoreCase = true)) return

    val textNeedle = settings.textFilter.trim()
    if (textNeedle.isNotBlank() && !message.contains(textNeedle, ignoreCase = true)) return

    WebhookSender.sendAsync(this, settings.webhookUrl, title.ifBlank { sbn.packageName }, message, sbn.packageName)
  }
}
