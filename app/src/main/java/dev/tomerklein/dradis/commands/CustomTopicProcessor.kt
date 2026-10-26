package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.settings.CustomTopic
import dev.tomerklein.dradis.settings.CustomTopicType
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.settings.NotifyIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The most recent value a custom topic produced, shown in the Topics screen. */
data class ReceivedValue(val label: String?, val value: String, val timeMillis: Long)

/** Side effects of a custom topic, injected so the processor stays unit-testable. */
interface CustomTopicEffects {
    fun notify(title: String, text: String, icon: NotifyIcon)
    fun speak(text: String)
    fun alarm(seconds: Int)
    /** Stores a received picture; false if it was rejected. */
    fun savePicture(image: ImagePayload.Image): Boolean
}

/**
 * Applies a [CustomTopic]'s processing type to an inbound payload. Payloads are
 * untrusted: text is capped before decoding and delivered text is length-limited.
 * Notify / speak / alarm honour the same global switches as the built-in commands.
 */
class CustomTopicProcessor(
    private val effects: CustomTopicEffects,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _values = MutableStateFlow<Map<String, ReceivedValue>>(emptyMap())

    /** Latest value per custom-topic id. */
    val values: StateFlow<Map<String, ReceivedValue>> = _values

    fun process(topic: CustomTopic, payload: ByteArray, settings: DradisSettings, log: (String) -> Unit) {
        val text = String(payload, 0, minOf(payload.size, PayloadExtractors.MAX_INPUT), Charsets.UTF_8)
        when (topic.type) {
            CustomTopicType.TEXT -> deliver(topic, null, text.trim(), settings)
            CustomTopicType.KEY_VALUE -> {
                val (label, value) = PayloadExtractors.keyValue(text, topic.separator, topic.name)
                deliver(topic, label, value, settings)
            }
            CustomTopicType.EXTRACTED -> {
                val value = PayloadExtractors.extract(text, topic.extractor)
                if (value == null) {
                    log("${topic.name}: extractor found no value")
                    return
                }
                deliver(topic, null, value, settings)
            }
            CustomTopicType.ALARM -> {
                if (!settings.pingEnabled) {
                    log("${topic.name}: alarm disabled in settings; ignoring")
                    return
                }
                effects.alarm(settings.alarmDurationSeconds)
                deliver(topic, null, "Alarm triggered", settings)
            }
            CustomTopicType.PICTURE -> {
                val image = ImagePayload.decode(payload)
                if (image == null || !effects.savePicture(image)) {
                    log("${topic.name}: picture rejected")
                    return
                }
                deliver(topic, null, "Picture received", settings)
            }
        }
        log("${topic.name}: ${topic.type.name.lowercase()} processed")
    }

    private fun deliver(topic: CustomTopic, label: String?, value: String, settings: DradisSettings) {
        val shown = value.take(MAX_VALUE_CHARS)
        _values.update { it + (topic.id to ReceivedValue(label, shown, clock())) }
        val title = (label ?: topic.name).take(MAX_TITLE_CHARS)
        val body = shown.take(MAX_NOTIFY_CHARS)
        if (topic.notify && settings.notifyEnabled) effects.notify(title, body, topic.icon)
        if (topic.speak && settings.ttsEnabled) effects.speak(if (label != null) "$label $body" else body)
    }

    /** Drops stored values for topics that no longer exist. */
    fun retain(ids: Set<String>) = _values.update { m -> m.filterKeys { it in ids } }

    private companion object {
        const val MAX_VALUE_CHARS = 500
        const val MAX_NOTIFY_CHARS = 200
        const val MAX_TITLE_CHARS = 64
    }
}
