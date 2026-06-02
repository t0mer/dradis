package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.mqtt.Topics
import kotlinx.serialization.json.Json

/**
 * On `say`, speaks the message via text-to-speech (CLAUDE.md §6.1). Payload is
 * JSON `{"text":"…"}`, or a raw-text body.
 */
class SayHandler(private val tts: TtsSpeaker) : CommandHandler {

    private val json = Json { ignoreUnknownKeys = true }

    override fun handles(topic: String, topics: Topics): Boolean =
        topic == topics.say

    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        if (!sink.settings.ttsEnabled) {
            sink.logInfo("TTS disabled in settings; ignoring")
            return
        }
        val text = payload.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<SayCommand>(it) }.getOrNull()?.text }
            ?.takeIf { it.isNotBlank() }
            ?: payload

        if (text.isBlank()) {
            sink.logInfo("Say ignored: empty text")
            return
        }
        tts.speak(text)
        sink.logInfo("Speaking: ${text.take(80)}")
    }
}
