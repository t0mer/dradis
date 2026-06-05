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
     *  at first setup and persisted so every device gets a unique id — duplicate
     *  client ids make the broker disconnect the older session. Not user-editable. */
    val clientId: String = "",
    /** Device fingerprint (ANDROID_ID) captured when [clientId] was generated.
     *  If the running device's fingerprint no longer matches, the settings were
     *  copied to another device (clone/restore) and [clientId] is regenerated so
     *  the two devices don't share an id. Not user-editable. */
    val clientIdDevice: String = "",

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
    val alarmDurationSeconds: Int = 3,
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

    /** Hold a Wi-Fi lock + partial wake lock while connected so the MQTT link
     *  survives device sleep (screen off on battery powers down the Wi-Fi radio /
     *  suspends the CPU, dropping the connection until the screen wakes). Required
     *  for the phone to stay remotely reachable while asleep; costs some battery. */
    val keepAliveWhileLocked: Boolean = true,
)
