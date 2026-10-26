package dev.tomerklein.dradis.telemetry

import dev.tomerklein.dradis.commands.BatteryInfo
import dev.tomerklein.dradis.commands.CommandSink
import dev.tomerklein.dradis.commands.LocationPayload
import dev.tomerklein.dradis.commands.WifiInfo
import dev.tomerklein.dradis.settings.OutboundSource
import dev.tomerklein.dradis.settings.OutboundTopic
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Where the current device values come from; faked in tests. */
interface DeviceValues {
    fun battery(): BatteryInfo
    fun wifi(): WifiInfo
    suspend fun location(): LocationPayload?
}

/**
 * Publishes each enabled [OutboundTopic] with the current value of its source. Battery and
 * Wi-Fi follow the telemetry switch, location follows the location switch (and needs a fix).
 * Never publishes an empty payload: on a retained topic that would clear it.
 */
class CustomOutboundPublisher(private val sink: CommandSink, private val values: DeviceValues) {

    private val json = Json { encodeDefaults = true }

    suspend fun publishAll(topics: List<OutboundTopic>) {
        val s = sink.settings
        val active = topics.filter { it.enabled }
        if (active.isEmpty()) return
        // Read each source at most once per round.
        val battery by lazy { values.battery() }
        val wifi by lazy { values.wifi() }
        var location: LocationPayload? = null
        var locationRead = false
        for (t in active) {
            val payload = when (t.source) {
                OutboundSource.BATTERY_LEVEL -> if (s.telemetryEnabled) battery.level.toString() else null
                OutboundSource.CHARGING -> if (s.telemetryEnabled) battery.charging.toString() else null
                OutboundSource.WIFI_SSID -> if (s.telemetryEnabled) wifi.ssid?.takeIf { it.isNotBlank() } ?: NO_WIFI else null
                OutboundSource.LOCATION -> if (s.locationEnabled) {
                    if (!locationRead) { location = values.location(); locationRead = true }
                    location?.let { json.encodeToString(it) }
                } else null
            }
            if (payload != null) sink.publish(t.topic, payload, t.retain)
        }
    }

    companion object {
        /** Published instead of a blank SSID, so the payload is never empty. */
        const val NO_WIFI = "none"
    }
}
