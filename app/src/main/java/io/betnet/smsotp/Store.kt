package io.betnet.smsotp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
  private const val PREF = "betnet_sms_settings"
  private const val KEY_LOGS = "delivery_logs"

  fun loadSettings(context: Context): AppSettings {
    val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    return AppSettings(
      webhookUrl = p.getString("url", "") ?: "",
      senderFilter = p.getString("sender", "") ?: "",
      textFilter = p.getString("text", "") ?: "",
      enabled = p.getBoolean("enabled", true)
    )
  }

  fun saveSettings(context: Context, s: AppSettings) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
      .putString("url", s.webhookUrl.trim())
      .putString("sender", s.senderFilter.trim())
      .putString("text", s.textFilter.trim())
      .putBoolean("enabled", s.enabled)
      .apply()
  }

  @Synchronized fun addLog(context: Context, log: DeliveryLog) {
    val logs = loadLogs(context).toMutableList()
    logs.add(0, log)
    while (logs.size > 200) logs.removeLast()
    val arr = JSONArray()
    logs.forEach { item ->
      arr.put(JSONObject().apply {
        put("id", item.id); put("time", item.time); put("sender", item.sender)
        put("message", item.message); put("package", item.packageName)
        put("status", item.status); put("httpCode", item.httpCode ?: JSONObject.NULL)
        put("response", item.response)
      })
    }
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_LOGS, arr.toString()).apply()
  }

  fun loadLogs(context: Context): List<DeliveryLog> {
    val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LOGS, "[]") ?: "[]"
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        DeliveryLog(
          id = o.optLong("id"), time = o.optLong("time"), sender = o.optString("sender"),
          message = o.optString("message"), packageName = o.optString("package"),
          status = o.optString("status"),
          httpCode = if (o.isNull("httpCode")) null else o.optInt("httpCode"),
          response = o.optString("response")
        )
      }
    } catch (_: Throwable) { emptyList() }
  }

  fun clearLogs(context: Context) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_LOGS).apply()
  }
}
