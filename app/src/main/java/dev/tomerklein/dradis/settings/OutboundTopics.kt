package dev.tomerklein.dradis.settings

import dev.tomerklein.dradis.mqtt.TopicFilter
import kotlinx.serialization.Serializable

/** The device value an [OutboundTopic] publishes. */
@Serializable
enum class OutboundSource { BATTERY_LEVEL, CHARGING, WIFI_SSID, LOCATION }

/** A user-defined topic the phone publishes one device value to (on connect and every update interval). */
@Serializable
data class OutboundTopic(
    val id: String,
    /** A concrete topic name: no wildcards. */
    val topic: String,
    val source: OutboundSource,
    val retain: Boolean = false,
    val enabled: Boolean = true,
)

object OutboundTopicValidator {

    const val MAX_TOPICS = 50
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

    /**
     * Error message for [t], or null if valid. [others] are the other outbound topics,
     * [builtIn] the app's own inbound filters and outbound topics, [customFilters] the
     * inbound custom filters: publishing onto any subscribed filter would echo the value
     * back into the app (or run a command), so those are refused.
     */
    fun validate(
        t: OutboundTopic,
        others: List<OutboundTopic>,
        builtIn: Collection<String>,
        customFilters: Collection<String>,
        reservedPrefix: String = "",
    ): String? {
        if (!ID_PATTERN.matches(t.id)) return "Invalid id"
        if (t.topic.isEmpty() || t.topic.length > TopicFilter.MAX_LENGTH || '\u0000' in t.topic) return "Invalid topic"
        if ('+' in t.topic || '#' in t.topic) return "A published topic cannot contain + or #"
        if (t.topic.startsWith("$")) return "Topics starting with \$ are reserved by the broker"
        if (t.topic.startsWith("/") || t.topic.endsWith("/") || "//" in t.topic) return "Empty topic level"
        // The whole `<prefix>/` namespace belongs to dradis/Zanzito devices (other phones' command topics).
        if (reservedPrefix.isNotEmpty() && t.topic.startsWith(reservedPrefix)) {
            return "Topics under \"$reservedPrefix\" belong to dradis devices; pick another topic"
        }
        if (builtIn.any { TopicFilter.overlaps(it, t.topic) }) return "Topic is one the app uses itself"
        if (customFilters.any { TopicFilter.matches(it, t.topic) }) return "Topic matches an inbound custom topic"
        if (others.any { it.id != t.id && it.topic == t.topic }) return "Topic is already published by another entry"
        return null
    }

    fun validateAll(
        list: List<OutboundTopic>,
        builtIn: Collection<String>,
        customFilters: Collection<String>,
        reservedPrefix: String = "",
    ): String? {
        if (list.size > MAX_TOPICS) return "At most $MAX_TOPICS outbound topics"
        if (list.map { it.id }.toSet().size != list.size) return "Duplicate ids"
        return list.firstNotNullOfOrNull { t ->
            validate(t, list, builtIn, customFilters, reservedPrefix)?.let { "${t.topic.take(64).ifBlank { t.id }}: $it" }
        }
    }

    /**
     * The entries of [list] that are still safe to publish given the CURRENT built-in topics, inbound
     * custom filters and prefix. Stored entries are re-checked at runtime because a prefix or device
     * rename can make a once-valid topic collide with a command or telemetry topic.
     */
    fun safe(
        list: List<OutboundTopic>,
        builtIn: Collection<String>,
        customFilters: Collection<String>,
        reservedPrefix: String,
    ): List<OutboundTopic> = list.filter { validate(it, emptyList(), builtIn, customFilters, reservedPrefix) == null }
}
