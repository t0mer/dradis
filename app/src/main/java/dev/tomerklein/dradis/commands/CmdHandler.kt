package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.log.Direction
import dev.tomerklein.dradis.log.LogFormatter
import dev.tomerklein.dradis.log.MqttLog
import dev.tomerklein.dradis.mqtt.Topics
import dev.tomerklein.dradis.telemetry.LocationPublisher

/**
 * Control verbs on `<base>/cmd/<verb>`. Like the other handlers, a blank payload
 * is ignored (a retained-clear must not fire a command); send `{}` to trigger.
 * Service-owned actions are injected so this class stays unit-testable.
 */
class CmdHandler(
    private val mqttLog: MqttLog,
    private val onHeartbeat: () -> Unit,
    private val onReconnect: () -> Unit,
    private val onRestart: () -> Unit,
    private val photo: CommandHandler = PhotoHandler(),
    private val stopAlarm: (log: (String) -> Unit) -> Unit = { PingPlayer.stop(it) },
    private val locate: suspend (CommandSink) -> LocationPayload? = { LocationPublisher.currentPayload(it) },
) : CommandHandler {

    override fun handles(topic: String, topics: Topics): Boolean = topics.cmdVerb(topic) != null

    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        val verb = sink.topics.cmdVerb(topic) ?: return
        if (payload.isBlank()) {
            sink.logInfo("cmd/$verb ignored: empty payload (retained-clear)")
            return
        }
        when (verb) {
            "take_picture" -> photo.handle(sink.topics.takePhoto, payload, sink)
            "send_heartbeat" -> onHeartbeat()
            "reconnect" -> onReconnect()
            "restart" -> onRestart()
            "stop_alarm" -> stopAlarm { sink.logInfo(it) }
            "set_bookmark" -> setBookmark(sink)
            "send_logs" -> sendLogs(sink, html = false)
            "send_html_logs" -> sendLogs(sink, html = true)
            else -> sink.logInfo("cmd/$verb: command not found")
        }
    }

    // Persisting bookmarks belongs to the map feature (CLAUDE.md step 6); until
    // then the fix is recorded in the in-app log only.
    private suspend fun setBookmark(sink: CommandSink) {
        val fix = locate(sink)
        if (fix == null) {
            sink.logInfo("Bookmark failed: location unavailable")
            return
        }
        sink.logInfo("Bookmark set at ${fix.latitude},${fix.longitude}")
    }

    private fun sendLogs(sink: CommandSink, html: Boolean) {
        val t = sink.topics
        // Our own dumps are logged as outbound entries; leave them out or each
        // dump would embed the previous ones and grow on every call.
        val all = mqttLog.entries.value.filter { it.topic != t.logInbound && it.topic != t.logOutbound }
        for ((dir, topic) in listOf(Direction.IN to t.logInbound, Direction.OUT to t.logOutbound)) {
            val entries = all.filter { it.direction == dir }
            val body = if (html) LogFormatter.toHtml(entries, topic) else LogFormatter.toText(entries)
            sink.publish(topic, body)
        }
    }
}
