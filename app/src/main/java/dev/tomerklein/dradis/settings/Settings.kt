package dev.tomerklein.dradis.settings

import dev.tomerklein.dradis.mqtt.Topics
import kotlinx.serialization.Serializable

/** Connection details for one MQTT broker. */
@Serializable
data class BrokerConfig(
    val host: String = "",
    val port: Int = 1883,
    val username: String = "",
    val password: String = "",
    val tls: Boolean = false,
    /** PEM-encoded CA certificate(s) to trust the broker's TLS cert. Blank →
     *  use the system trust store (public CAs). Needed for self-signed/private CAs. */
    val caCert: String = "",
) {
    val isConfigured: Boolean get() = host.isNotBlank() && port in 1..65535
}

/** All user-editable settings (CLAUDE.md §2). Persisted as a single JSON blob
 *  in Preferences DataStore. */
@Serializable
data class DradisSettings(
    val deviceName: String = "phone",
    val topicPrefix: String = Topics.DEFAULT_PREFIX,

    /** Stable, per-install MQTT client identifier. Generated once (UUID-based)
     *  on first run and persisted so every device gets a unique id — duplicate
     *  client ids make the broker disconnect the older session. Not user-editable. */
    val clientId: String = "",

    val lanBroker: BrokerConfig = BrokerConfig(port = 1883),
    val wanBroker: BrokerConfig = BrokerConfig(port = 8883, tls = true),
    val homeSsids: List<String> = emptyList(),

    // Periodic publishing of both telemetry and location at the update interval.
    val periodicUpdatesEnabled: Boolean = true,
    val updateIntervalSeconds: Int = 90,

    // Per-feature toggles.
    val smsEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val pingEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val telemetryEnabled: Boolean = true,
    val notifyEnabled: Boolean = true,
    val sensorsEnabled: Boolean = true,
    val ttsEnabled: Boolean = true,

    // SMS options.
    val smsNotifyOnSend: Boolean = false,

    // Location options.
    val locationHighAccuracy: Boolean = true,

    // Alarm / find-phone options.
    val alarmDurationSeconds: Int = 30,
    val alarmOverrideDnd: Boolean = true,
    /** Ringtone URI to play for the alarm; blank → system default alarm. */
    val alarmRingtoneUri: String = "",
    val alarmRingtoneTitle: String = "",

    // Camera options — default camera when a takephoto request omits one.
    val cameraDefaultRear: Boolean = true,

    // Voice / TTS options.
    val notifyReadAloud: Boolean = false,

    // Home Assistant MQTT discovery.
    val homeAssistantEnabled: Boolean = true,
    val homeAssistantPrefix: String = "homeassistant",

    val autostartOnBoot: Boolean = true,
    val reconnectOnNetworkChange: Boolean = true,
)
