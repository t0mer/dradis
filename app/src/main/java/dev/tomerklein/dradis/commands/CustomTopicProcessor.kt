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
    /** [key] identifies the topic so a repeat replaces its previous notification. */
    fun notify(key: String, title: String, text: String, icon: NotifyIcon)
    fun speak(text: String)
    fun alarm(seconds: Int)
    /** Stores a received picture; false if it was rejected. */
    fun savePicture(image: ImagePayload.Image): Boolean
}

/**
 * Applies a [CustomTopic]'s processing type to an inbound payload. Payloads are
 * untrusted: blank payloads are ignored (a broker "clear topic" arrives as an empty
 * message), text is capped before decoding, delivered text is length-limited, and
 * each topic accepts at most one message per [cooldownMs]. Notify / speak / alarm
 * honour the same global switches as the built-in commands.
 */
class CustomTopicProcessor(
    private val effects: CustomTopicEffects,
    private val _values: MutableStateFlow<Map<String, ReceivedValue>> = MutableStateFlow(emptyMap()),
    private val clock: () -> Long = System::currentTimeMillis,
    private val cooldownMs: Long = COOLDOWN_MS,
) {
    /** Latest value per custom-topic id. */
    val values: StateFlow<Map<String, ReceivedValue>> = _values

    private val lastAccepted = HashMap<String, Long>()

    fun process(topic: CustomTopic, payload: ByteArray, settings: DradisSettings, log: (String) -> Unit) {
        val text = String(payload, 0, minOf(payload.size, PayloadExtractors.MAX_INPUT), Charsets.UTF_8)
        if (payload.isEmpty() || text.isBlank()) {
            log("${topic.name}: ignored empty payload (retained-clear)")
            return
        }
        if (!withinRate(topic.id)) return // silent: a flood must not also flood the log
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
                if (settings.alarmDurationSeconds <= 0) {
                    log("${topic.name}: alarm duration is 0; ignoring")
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

    private fun withinRate(id: String): Boolean = synchronized(lastAccepted) {
        val now = clock()
        val last = lastAccepted[id]
        if (last != null && now - last < cooldownMs) return false
        lastAccepted[id] = now
        true
    }

    // The title is always the topic's own name: payload-controlled text must not
    // be able to pose as a system or security alert.
    private fun deliver(topic: CustomTopic, label: String?, value: String, settings: DradisSettings) {
        val shown = value.take(MAX_VALUE_CHARS)
        _values.update { it + (topic.id to ReceivedValue(label, shown, clock())) }
        val body = (if (label != null) "$label: $shown" else shown).take(MAX_NOTIFY_CHARS)
        if (topic.notify && settings.notifyEnabled) effects.notify(topic.id, topic.name.take(MAX_TITLE_CHARS), body, topic.icon)
        if (topic.speak && settings.ttsEnabled) effects.speak(body)
    }

    /** Drops stored values and rate state for topics that no longer exist. */
    fun retain(ids: Set<String>) {
        _values.update { m -> m.filterKeys { it in ids } }
        synchronized(lastAccepted) { lastAccepted.keys.retainAll(ids) }
    }

    companion object {
        /** Minimum time between accepted messages for one topic. */
        const val COOLDOWN_MS = 2_000L
        private const val MAX_VALUE_CHARS = 500
        private const val MAX_NOTIFY_CHARS = 200
        private const val MAX_TITLE_CHARS = 64
    }
}
