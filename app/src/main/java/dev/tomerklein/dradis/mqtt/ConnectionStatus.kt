package dev.tomerklein.dradis.mqtt

/** Which broker the selector picked for the current network. */
enum class BrokerKind { LAN, WAN, NONE }

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED }

/** Snapshot of the live MQTT connection, surfaced on the Status screen. */
data class ConnectionStatus(
    val state: ConnState = ConnState.DISCONNECTED,
    val broker: BrokerKind = BrokerKind.NONE,
    val ssid: String? = null,
    val host: String? = null,
    val detail: String = "",
)
