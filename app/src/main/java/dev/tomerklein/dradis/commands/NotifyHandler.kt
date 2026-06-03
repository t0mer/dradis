package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.mqtt.Topics
import kotlinx.serialization.json.Json

/**
 * On `notify`, posts a notification to the device's shade (CLAUDE.md §6.1).
 * Payload is JSON `{"title","text","id"?}`; a non-JSON body is used as the
 * notification text. When "read notifications aloud" is enabled, the message is
 * also spoken via text-to-speech.
 */
class NotifyHandler(private val tts: TtsSpeaker) : CommandHandler {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun handles(topic: String, topics: Topics): Boolean =
        topic == topics.notify

    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        if (!sink.settings.notifyEnabled) {
            sink.logInfo("Notifications disabled in settings; ignoring")
            return
        }
        val cmd = payload.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<NotifyCommand>(it) }.getOrNull() }
            ?: NotifyCommand(text = payload)

        if (cmd.title.isBlank() && cmd.text.isBlank()) {
            sink.logInfo("Notify ignored: empty payload")
            return
        }

        val id = cmd.id ?: Notifier.nextId()
        val posted = Notifier.post(sink.appContext, cmd.title.ifBlank { "DRADIS" }, cmd.text, id)
        if (posted) {
            sink.logInfo("Notification posted (id=$id): ${cmd.title}")
        } else {
            sink.logInfo("Notify requested but notifications are disabled for DRADIS")
        }

        if (sink.settings.notifyReadAloud && sink.settings.ttsEnabled) {
            val spoken = listOf(cmd.title, cmd.text).filter { it.isNotBlank() }.joinToString(". ")
            tts.speak(spoken)
        }
    }
}
