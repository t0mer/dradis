package dev.tomerklein.dradis.commands

import android.content.Context
import dev.tomerklein.dradis.mqtt.Topics
import dev.tomerklein.dradis.settings.DradisSettings

/**
 * Everything a command handler or telemetry reporter needs from the service:
 * an Android context, the current topic map + settings, and publish/log hooks.
 * The [dev.tomerklein.dradis.mqtt.MqttService] implements this.
 */
interface CommandSink {
    val appContext: Context
    val topics: Topics
    val settings: DradisSettings

    /** Current Wi-Fi SSID, or null when not on Wi-Fi / unreadable. */
    val currentSsid: String?

    /** True when the active network is Wi-Fi. */
    val onWifi: Boolean

    fun publish(topic: String, payload: String, retain: Boolean = false)
    fun publish(topic: String, payload: ByteArray, retain: Boolean = false)
    fun logInfo(message: String)
}

/** One inbound command type. Implementations must be self-contained and never
 *  throw out (CLAUDE.md §14): a failing handler publishes an error, it does not
 *  drop the MQTT connection. */
interface CommandHandler {
    /** True if this handler owns [topic] given the active [Topics] map. */
    fun handles(topic: String, topics: Topics): Boolean

    /** Handle an inbound message. Runs off the MQTT callback thread. */
    suspend fun handle(topic: String, payload: String, sink: CommandSink)
}
