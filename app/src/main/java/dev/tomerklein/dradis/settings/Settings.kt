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
) {
    val isConfigured: Boolean get() = host.isNotBlank() && port in 1..65535
}

/** All user-editable settings (CLAUDE.md §2). Persisted as a single JSON blob
 *  in Preferences DataStore. */
@Serializable
data class DradisSettings(
    val deviceName: String = "phone",
    val topicPrefix: String = Topics.DEFAULT_PREFIX,

    val lanBroker: BrokerConfig = BrokerConfig(port = 1883),
    val wanBroker: BrokerConfig = BrokerConfig(port = 8883, tls = true),
    val homeSsids: List<String> = emptyList(),

    val locationPublishEnabled: Boolean = false,
    val locationIntervalSeconds: Int = 300,

    // Per-feature toggles.
    val smsEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val pingEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val telemetryEnabled: Boolean = true,
    val notifyEnabled: Boolean = true,

    val autostartOnBoot: Boolean = true,
    val reconnectOnNetworkChange: Boolean = true,
)
