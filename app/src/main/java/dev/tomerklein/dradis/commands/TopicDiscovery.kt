package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.log.LogFormatter
import dev.tomerklein.dradis.mqtt.TopicFilter
import dev.tomerklein.dradis.settings.CustomTopicType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A topic seen while discovery was running. Payload content is only ever shown as a redacted preview. */
data class DiscoveredTopic(
    val topic: String,
    val count: Int,
    val lastSeenMillis: Long,
    val lastSize: Int,
    val preview: String,
    val retained: Boolean,
    val suggested: CustomTopicType,
)

/**
 * Passive topic discovery: while a session is active the service subscribes to the
 * session filter and feeds every message it does not otherwise handle to [record].
 * Opt-in and time-boxed; bounded in topics, topic length and preview size.
 */
class TopicDiscovery(private val clock: () -> Long = System::currentTimeMillis) {

    data class Session(val filter: String, val deadlineMillis: Long)

    private val _session = MutableStateFlow<Session?>(null)
    /** The running session, or null. The service subscribes/unsubscribes from this. */
    val session: StateFlow<Session?> = _session

    private val _topics = MutableStateFlow<List<DiscoveredTopic>>(emptyList())
    val topics: StateFlow<List<DiscoveredTopic>> = _topics

    private val seen = LinkedHashMap<String, DiscoveredTopic>()

    /** Starts a session (clearing earlier results). Returns an error message, or null. */
    fun start(filter: String, seconds: Int): String? {
        if (!TopicFilter.isValid(filter)) return "Not a valid MQTT topic filter"
        if (seconds !in 1..MAX_SECONDS) return "Duration must be 1 to $MAX_SECONDS seconds"
        synchronized(seen) {
            seen.clear()
            _topics.value = emptyList()
        }
        _session.value = Session(filter, clock() + seconds * 1000L)
        return null
    }

    fun stop() {
        _session.value = null
    }

    val isActive: Boolean get() = _session.value?.let { clock() < it.deadlineMillis } ?: false

    fun record(topic: String, bytes: ByteArray, retained: Boolean) {
        val s = _session.value ?: return
        val now = clock()
        if (now >= s.deadlineMillis || topic.length > TopicFilter.MAX_LENGTH || topic.startsWith("$")) return
        if (!TopicFilter.matches(s.filter, topic)) return
        synchronized(seen) {
            val prev = seen[topic]
            if (prev == null && seen.size >= MAX_TOPICS) return
            seen[topic] = DiscoveredTopic(
                topic = topic,
                count = (prev?.count ?: 0) + 1,
                lastSeenMillis = now,
                lastSize = bytes.size,
                preview = preview(bytes),
                retained = retained,
                suggested = suggest(bytes),
            )
            _topics.value = seen.values.sortedByDescending { it.lastSeenMillis }
        }
    }

    companion object {
        const val MAX_TOPICS = 200
        const val MAX_SECONDS = 300
        const val DEFAULT_SECONDS = 60
        private const val PREVIEW_CHARS = 80
        private const val HEAD_BYTES = 256

        /** Redacted, single-line, length-capped text; binary data shows only its size. */
        fun preview(bytes: ByteArray): String {
            if (bytes.isEmpty()) return ""
            if (ImagePayload.looksLikeImage(bytes)) return "<${bytes.size} bytes image>"
            val head = String(bytes, 0, minOf(bytes.size, HEAD_BYTES), Charsets.UTF_8)
            // A cut at HEAD_BYTES may split a multi-byte character; that is not binary.
            val checked = if (bytes.size > HEAD_BYTES) head.dropLast(1) else head
            if (checked.any { it == '�' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') }) {
                return "<${bytes.size} bytes>"
            }
            val flat = LogFormatter.redactPii(head).replace(Regex("\\s+"), " ").trim()
            return if (flat.length > PREVIEW_CHARS) flat.take(PREVIEW_CHARS) + "…" else flat
        }

        fun suggest(bytes: ByteArray): CustomTopicType {
            if (ImagePayload.looksLikeImage(bytes)) return CustomTopicType.PICTURE
            if (bytes.size > 64 * 1024) return CustomTopicType.TEXT
            val text = String(bytes, Charsets.UTF_8).trim()
            if (text.startsWith("{") && runCatching { Json.parseToJsonElement(text) is JsonObject }.getOrDefault(false)) {
                return CustomTopicType.EXTRACTED
            }
            return CustomTopicType.TEXT
        }
    }
}
