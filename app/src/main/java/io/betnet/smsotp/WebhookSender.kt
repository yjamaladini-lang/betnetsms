package io.betnet.smsotp

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

object WebhookSender {
  fun sendAsync(context: Context, url: String, sender: String, message: String, packageName: String, isTest: Boolean = false, onDone: ((DeliveryLog) -> Unit)? = null) {
    val app = context.applicationContext
    thread {
      val now = System.currentTimeMillis()
      val result = try {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Webhook URL is invalid" }
        val body = JSONObject().apply {
          put("sender", sender)
          put("message", message)
          put("timestamp", now)
          put("package_name", packageName)
          put("source", "betnet-sms-otp")
          put("test", isTest)
        }.toString()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 15000
          readTimeout = 15000
          doOutput = true
          setRequestProperty("Content-Type", "application/json; charset=utf-8")
          setRequestProperty("Accept", "application/json, text/plain, */*")
          setRequestProperty("User-Agent", "Betnet-sms-otp/1.0")
        }
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }?.take(2000) ?: ""
        DeliveryLog(now, now, sender, message, packageName, if (code in 200..299) "SENT" else "FAILED", code, response)
      } catch (e: Throwable) {
        DeliveryLog(now, now, sender, message, packageName, "FAILED", null, e.message ?: e.javaClass.simpleName)
      }
      Store.addLog(app, result)
      onDone?.invoke(result)
    }
  }
}
