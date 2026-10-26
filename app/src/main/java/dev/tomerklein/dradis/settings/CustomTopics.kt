package dev.tomerklein.dradis.settings

import kotlinx.serialization.Serializable

/** How an inbound custom topic's payload is processed. */
@Serializable
enum class CustomTopicType { TEXT, ALARM, PICTURE, KEY_VALUE, EXTRACTED }

/** Notification icon choices (mapped to system drawables where they are posted). */
@Serializable
enum class NotifyIcon { DEFAULT, ALARM, CAMERA, INFO }

/**
 * A user-defined inbound topic: the phone subscribes to [filter] and processes
 * each message according to [type]. Persisted by [CustomTopicsRepository].
 */
@Serializable
data class CustomTopic(
    val id: String,
    val name: String,
    /** MQTT topic filter; may contain `+` and a trailing `#`. */
    val filter: String,
    val type: CustomTopicType = CustomTopicType.TEXT,
    val enabled: Boolean = true,
    val notify: Boolean = false,
    val speak: Boolean = false,
    val icon: NotifyIcon = NotifyIcon.DEFAULT,
    /** EXTRACTED only: `$.a.b[0]` JSONPath subset, otherwise a regex. */
    val extractor: String = "",
    /** KEY_VALUE only: the first occurrence splits label from value. */
    val separator: String = ":",
)
