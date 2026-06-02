package dev.tomerklein.dradis.mqtt

/**
 * Single source of truth for every MQTT topic string (CLAUDE.md §5, §6).
 * No raw topic strings may appear anywhere else in the codebase.
 *
 * Base layout: `<prefix>/<device>/<leaf>` — e.g. `dradis/tomer/sendsms`.
 * Prefix defaults to `dradis`; set it to `zanzito` for the legacy backend.
 */
class Topics(private val prefix: String, private val device: String) {

    private val base: String get() = "$prefix/$device"

    // --- Inbound (app subscribes) -------------------------------------------
    val sendSms: String get() = "$base/sendsms"
    /** Legacy Zanzito form: number in the path, raw body = text. */
    val sendSmsLegacyPrefix: String get() = "$base/sendsms/"
    val getLocation: String get() = "$base/getlocation"
    val ping: String get() = "$base/ping"
    val takePhoto: String get() = "$base/takephoto"
    val getStatus: String get() = "$base/getstatus"
    val notify: String get() = "$base/notify"

    /** All inbound command topics, for explicit subscription (preferred over wildcard). */
    fun inboundTopics(): List<String> = listOf(
        sendSms,
        // Legacy SMS uses a single-level wildcard to capture the phone segment.
        "$base/sendsms/+",
        getLocation,
        ping,
        takePhoto,
        getStatus,
        notify,
    )

    // --- Outbound (app publishes) -------------------------------------------
    val status: String get() = "$base/status"
    val version: String get() = "$base/version"
    val deviceInfo: String get() = "$base/device_info"
    val battery: String get() = "$base/battery"
    val wifi: String get() = "$base/wifi"
    val sensors: String get() = "$base/sensors"
    val location: String get() = "$base/location"
    val photo: String get() = "$base/photo"
    val smsResult: String get() = "$base/sendsms/result"
    val log: String get() = "$base/log"

    companion object {
        const val DEFAULT_PREFIX = "dradis"

        /** True if [topic] matches the legacy `.../sendsms/<phone>` form (and is
         *  not the preferred `.../sendsms` nor the `.../sendsms/result` topic). */
        fun isLegacySms(topic: String, prefix: String, device: String): Boolean {
            val legacyBase = "$prefix/$device/sendsms/"
            return topic.startsWith(legacyBase) && topic != "$prefix/$device/sendsms/result"
        }

        /** Extracts the phone segment from a legacy SMS topic, or null. */
        fun legacySmsPhone(topic: String, prefix: String, device: String): String? {
            val legacyBase = "$prefix/$device/sendsms/"
            if (!topic.startsWith(legacyBase)) return null
            return topic.removePrefix(legacyBase).takeIf { it.isNotEmpty() && it != "result" }
        }
    }
}
