package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.mqtt.Topics
import kotlinx.serialization.json.Json

/**
 * On `ping`, sounds the find-my-phone alarm through silent/DND (CLAUDE.md §9.4).
 * Optional `{"seconds":N}`; `{"seconds":0}` stops an active alarm.
 */
class PingHandler : CommandHandler {

    private val json = Json { ignoreUnknownKeys = true }

    override fun handles(topic: String, topics: Topics): Boolean =
        topic == topics.ping

    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        if (!sink.settings.pingEnabled) {
            sink.logInfo("Ping disabled in settings; ignoring")
            return
        }
        // An empty payload is not a command — it's how brokers/tools (e.g.
        // MQTT Explorer's "delete topic") clear a retained message. Acting on it
        // would fire the alarm spuriously. Require explicit content to trigger.
        if (payload.isBlank()) {
            sink.logInfo("Ping ignored: empty payload (retained-clear)")
            return
        }
        val seconds = payload.takeIf { it.isNotBlank() && it.trim() != "{}" }
            ?.let { runCatching { json.decodeFromString<PingCommand>(it) }.getOrNull()?.seconds }
            ?: sink.settings.alarmDurationSeconds

        if (seconds <= 0) {
            PingPlayer.stop { msg -> sink.logInfo(msg) }
        } else {
            PingPlayer.start(
                sink.appContext, seconds, sink.settings.alarmOverrideDnd,
                sink.settings.alarmRingtoneUri,
            ) { msg -> sink.logInfo(msg) }
        }
    }
}
