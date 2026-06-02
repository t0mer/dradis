package dev.tomerklein.dradis.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dev.tomerklein.dradis.mqtt.Topics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Sends an SMS on `sendsms` (CLAUDE.md §9.2). Accepts both the preferred JSON
 * form `{phone,text}` and the legacy Zanzito form (phone in the topic path,
 * raw body = text). Publishes a result to `sendsms/result`.
 */
class SmsHandler : CommandHandler {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun handles(topic: String, topics: Topics): Boolean =
        topic == topics.sendSms ||
            (topic.startsWith(topics.sendSmsLegacyPrefix) && topic != topics.smsResult)

    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        if (!sink.settings.smsEnabled) {
            sink.logInfo("SMS disabled in settings; ignoring")
            return
        }
        val topics = sink.topics
        val (phone, text) = parse(topic, payload, topics)

        if (phone.isBlank() || text.isBlank()) {
            publishResult(sink, phone, ok = false, error = "missing phone or text")
            return
        }
        if (ContextCompat.checkSelfPermission(sink.appContext, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            publishResult(sink, phone, ok = false, error = "SEND_SMS permission not granted")
            return
        }

        try {
            val sms = smsManager(sink.appContext)
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            sink.logInfo("SMS sent to $phone (${parts.size} part(s))")
            publishResult(sink, phone, ok = true)
        } catch (t: Throwable) {
            publishResult(sink, phone, ok = false, error = t.message ?: "send failed")
        }
    }

    private fun parse(topic: String, payload: String, topics: Topics): Pair<String, String> {
        return if (topic == topics.sendSms) {
            val cmd = runCatching { json.decodeFromString<SmsCommand>(payload) }.getOrNull()
            (cmd?.phone ?: "") to (cmd?.text ?: "")
        } else {
            val phone = topic.removePrefix(topics.sendSmsLegacyPrefix)
            phone to payload
        }
    }

    private fun publishResult(sink: CommandSink, phone: String, ok: Boolean, error: String? = null) {
        sink.publish(sink.topics.smsResult, json.encodeToString(SmsResult(phone, ok, error)))
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
}
