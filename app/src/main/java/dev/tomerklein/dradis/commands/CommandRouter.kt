package dev.tomerklein.dradis.commands

import android.util.Log
import dev.tomerklein.dradis.mqtt.Topics

private const val TAG = "dradis.cmd"

/**
 * Dispatches inbound MQTT messages to the first matching [CommandHandler].
 * Each handler is invoked inside its own try/catch so one failing command can
 * never crash the service or drop the connection (CLAUDE.md §14).
 */
class CommandRouter(
    private val sink: CommandSink,
    private val handlers: List<CommandHandler>,
) {
    suspend fun handle(topic: String, payload: String) {
        val topics: Topics = sink.topics
        val handler = handlers.firstOrNull { it.handles(topic, topics) }
        if (handler == null) {
            Log.d(TAG, "no handler for $topic")
            return
        }
        try {
            handler.handle(topic, payload, sink)
        } catch (t: Throwable) {
            Log.e(TAG, "handler ${handler::class.simpleName} failed for $topic", t)
            sink.logInfo("Error handling $topic: ${t.message}")
        }
    }
}
