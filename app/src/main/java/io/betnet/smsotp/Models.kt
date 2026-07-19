package io.betnet.smsotp

data class AppSettings(
  val webhookUrl: String = "",
  val senderFilter: String = "",
  val textFilter: String = "",
  val enabled: Boolean = true
)

data class DeliveryLog(
  val id: Long,
  val time: Long,
  val sender: String,
  val message: String,
  val packageName: String,
  val status: String,
  val httpCode: Int? = null,
  val response: String = ""
)
