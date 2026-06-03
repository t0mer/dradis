package dev.tomerklein.dradis.commands

import android.util.Log
import dev.tomerklein.dradis.mqtt.Topics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * On `ping`, sounds the find-my-phone alarm through silent/DND (CLAUDE.md §9.4).
 * Optional `{"seconds":N}`; `{"seconds":0}` (or any non-positive value) stops an
 * active alarm. An empty payload is ignored (e.g. a retained-clear). A non-JSON
 * payload triggers the alarm for the configured default duration.
 */
class PingHandler : CommandHandler {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun handles(topic: String, topics: Topics): Boolean =
        topic == topics.ping

    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        if (!sink.settings.pingEnabled) {
            sink.logInfo("Ping disabled in settings; ignoring")
            return
        }
        // An empty payload is not a command — it's how brokers/tools (e.g.
        // MQTT Explorer's "delete topic") clear a retained message.
        if (payload.isBlank()) {
            sink.logInfo("Ping ignored: empty payload (retained-clear)")
            return
        }

        // Extract an explicit duration if present. Accept `seconds` as a JSON
        // number OR a quoted string ("0"), so a value of 0/negative reliably
        // STOPS instead of falling through to the default and ringing. Only when
        // no parseable duration is given do we fall back to the default.
        val explicit: Int? = runCatching {
            json.parseToJsonElement(payload.trim()).jsonObject["seconds"]
                ?.jsonPrimitive
                ?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }
        }.getOrNull()
        val seconds = explicit ?: sink.settings.alarmDurationSeconds
        Log.i(TAG, "ping payload='$payload' explicit=$explicit -> seconds=$seconds")

        if (seconds <= 0) {
            PingPlayer.stop { msg -> sink.logInfo(msg) }
        } else {
            PingPlayer.start(
                sink.appContext, seconds, sink.settings.alarmOverrideDnd,
                sink.settings.alarmRingtoneUri,
            ) { msg -> sink.logInfo(msg) }
        }
    }

    companion object {
        private const val TAG = "dradis.ping"
    }
}
