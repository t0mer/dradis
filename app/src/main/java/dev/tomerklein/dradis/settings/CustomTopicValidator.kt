package dev.tomerklein.dradis.settings

import dev.tomerklein.dradis.mqtt.TopicFilter

/** Validation for user-defined topics (also used to vet imported JSON). */
object CustomTopicValidator {

    const val MAX_TOPICS = 100
    private const val MAX_NAME = 64
    private const val MAX_EXTRACTOR = 200
    private const val MAX_SEPARATOR = 8
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

    /** Error message for [topic], or null if valid. [others] are the other topics
     *  in the list; [builtIn] are the app's own inbound filters and outbound topics. */
    fun validate(topic: CustomTopic, others: List<CustomTopic>, builtIn: Collection<String>): String? {
        if (!ID_PATTERN.matches(topic.id)) return "Invalid id"
        if (topic.name.isBlank() || topic.name.length > MAX_NAME) return "Name must be 1-$MAX_NAME characters"
        if (!TopicFilter.isValid(topic.filter)) return "Invalid MQTT topic filter"
        if (builtIn.any { TopicFilter.overlaps(it, topic.filter) }) return "Topic overlaps one the app uses itself"
        if (others.any { it.id != topic.id && TopicFilter.overlaps(it.filter, topic.filter) }) {
            return "Topic overlaps another custom topic"
        }
        when (topic.type) {
            CustomTopicType.EXTRACTED -> {
                if (topic.extractor.isBlank() || topic.extractor.length > MAX_EXTRACTOR) {
                    return "Extractor must be 1-$MAX_EXTRACTOR characters"
                }
                if (!topic.extractor.startsWith("$") && runCatching { Regex(topic.extractor) }.isFailure) {
                    return "Invalid regular expression"
                }
            }
            CustomTopicType.KEY_VALUE ->
                if (topic.separator.isEmpty() || topic.separator.length > MAX_SEPARATOR) {
                    return "Separator must be 1-$MAX_SEPARATOR characters"
                }
            else -> Unit
        }
        return null
    }

    /** First error found in [topics], or null if the whole list is valid. */
    fun validateAll(topics: List<CustomTopic>, builtIn: Collection<String>): String? {
        if (topics.size > MAX_TOPICS) return "At most $MAX_TOPICS custom topics"
        if (topics.map { it.id }.toSet().size != topics.size) return "Duplicate ids"
        return topics.firstNotNullOfOrNull { t ->
            validate(t, topics, builtIn)?.let { "${t.name.ifBlank { t.id }}: $it" }
        }
    }
}
