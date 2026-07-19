package io.betnet.smsotp

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MaterialTheme { BetnetApp() } }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetnetApp() {
  val context = LocalContext.current
  var settings by remember { mutableStateOf(Store.loadSettings(context)) }
  var logs by remember { mutableStateOf(Store.loadLogs(context)) }
  var tab by remember { mutableIntStateOf(0) }
  var testText by remember { mutableStateOf("") }

  Scaffold(topBar = { TopAppBar(title = { Text("Betnet sms otp") }) }) { pad ->
    Column(Modifier.padding(pad).fillMaxSize()) {
      TabRow(selectedTabIndex = tab) {
        Tab(tab == 0, { tab = 0 }, text = { Text("تنظیمات") })
        Tab(tab == 1, { logs = Store.loadLogs(context); tab = 1 }, text = { Text("گزارش ارسال") })
      }
      if (tab == 0) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          item {
            Text("برنامه متن اعلان پیامک را می‌خواند و با فیلترهای زیر به Webhook می‌فرستد.")
          }
          item {
            OutlinedTextField(settings.webhookUrl, { settings = settings.copy(webhookUrl = it) }, label = { Text("Webhook URL") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
          }
          item {
            OutlinedTextField(settings.senderFilter, { settings = settings.copy(senderFilter = it) }, label = { Text("فیلتر فرستنده (اختیاری)") }, supportingText = { Text("در عنوان اعلان یا نام پکیج جستجو می‌شود؛ مثال: Bank Mellat") }, modifier = Modifier.fillMaxWidth())
          }
          item {
            OutlinedTextField(settings.textFilter, { settings = settings.copy(textFilter = it) }, label = { Text("عبارت داخل پیامک (اختیاری)") }, supportingText = { Text("مثال: واریز یا ریال") }, modifier = Modifier.fillMaxWidth())
          }
          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Switch(settings.enabled, { settings = settings.copy(enabled = it) })
              Spacer(Modifier.width(10.dp)); Text("ارسال خودکار فعال باشد")
            }
          }
          item {
            Button(onClick = { Store.saveSettings(context, settings); testText = "تنظیمات ذخیره شد" }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره تنظیمات") }
          }
          item {
            OutlinedButton(onClick = {
              context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }, modifier = Modifier.fillMaxWidth()) { Text("فعال‌کردن دسترسی خواندن اعلان‌ها") }
          }
          item {
            OutlinedButton(onClick = {
              Store.saveSettings(context, settings)
              if (settings.webhookUrl.isBlank()) testText = "ابتدا URL را وارد کن" else {
                testText = "در حال ارسال تست..."
                WebhookSender.sendAsync(context, settings.webhookUrl, "BETNET-TEST", "این یک پیام تست از Betnet sms otp است", context.packageName, true) {
                  runCatching { (context as MainActivity).runOnUiThread { testText = "نتیجه تست: ${it.status} ${it.httpCode ?: ""} ${it.response.take(120)}"; logs = Store.loadLogs(context) } }
                }
              }
            }, modifier = Modifier.fillMaxWidth()) { Text("تست Webhook") }
          }
          if (testText.isNotBlank()) item { Text(testText, fontWeight = FontWeight.SemiBold) }
          item {
            Text("نکته: برای پیامک‌های طولانی، نمایش کامل متن به تنظیمات اعلان برنامه پیام‌رسان بستگی دارد.", style = MaterialTheme.typography.bodySmall)
          }
        }
      } else {
        Column(Modifier.fillMaxSize()) {
          Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("آخرین ${logs.size} مورد")
            Row {
              TextButton(onClick = { logs = Store.loadLogs(context) }) { Text("بروزرسانی") }
              TextButton(onClick = { Store.clearLogs(context); logs = emptyList() }) { Text("پاک‌کردن") }
            }
          }
          LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(logs, key = { it.id }) { log -> LogCard(log) }
          }
        }
      }
    }
  }
}

@Composable
private fun LogCard(log: DeliveryLog) {
  val date = remember(log.time) { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(log.time)) }
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(log.sender.ifBlank { log.packageName }, fontWeight = FontWeight.Bold)
        Text(if (log.status == "SENT") "ارسال شد" else "ناموفق", color = if (log.status == "SENT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
      }
      Text(log.message, maxLines = 5)
      Text("HTTP: ${log.httpCode ?: "-"} | $date", style = MaterialTheme.typography.bodySmall)
      if (log.response.isNotBlank()) Text("پاسخ: ${log.response}", style = MaterialTheme.typography.bodySmall)
    }
  }
}
