package dev.tomerklein.dradis.mqtt

import dev.tomerklein.dradis.commands.CommandSink
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Publishes Home Assistant MQTT-discovery config topics so DRADIS appears in HA
 * as one device with a device_tracker, sensors, binary sensors and command
 * buttons (https://www.home-assistant.io/integrations/mqtt/#mqtt-discovery).
 *
 * Configs are retained so HA picks them up on (re)start. When discovery is
 * disabled the same config topics are cleared (empty retained payload).
 */
class HassDiscovery(private val sink: CommandSink) {

    /** Publish (or, when disabled, clear) all discovery configs. */
    fun publish() {
        val enabled = sink.settings.homeAssistantEnabled
        for ((topic, payload) in configs()) {
            sink.publish(topic, if (enabled) payload else "", retain = true)
        }
    }

    private fun configs(): List<Pair<String, String>> {
        val s = sink.settings
        val t = sink.topics
        val device = s.deviceName
        val discoveryPrefix = s.homeAssistantPrefix.ifBlank { "homeassistant" }
        val nodeId = "dradis_$device"

        val deviceBlock = buildJsonObject {
            putJsonArray("identifiers") { add(nodeId) }
            put("name", "DRADIS $device")
            put("manufacturer", "DRADIS")
            put("model", "DRADIS")
        }

        fun config(component: String, objectId: String, body: JsonObjectBuilder.() -> Unit): Pair<String, String> {
            val payload: JsonObject = buildJsonObject {
                body()
                put("unique_id", "${nodeId}_$objectId")
                put("availability_topic", t.status)
                put("payload_available", "1")
                put("payload_not_available", "0")
                put("device", deviceBlock)
            }
            val topic = "$discoveryPrefix/$component/$nodeId/$objectId/config"
            return topic to payload.toString()
        }

        fun boolTemplate(field: String) = "{{ 'ON' if value_json.$field else 'OFF' }}"

        return listOf(
            config("device_tracker", "location") {
                put("name", "Location")
                put("json_attributes_topic", t.location)
                put("source_type", "gps")
            },
            config("sensor", "battery") {
                put("name", "Battery")
                put("state_topic", t.battery)
                put("value_template", "{{ value_json.battery_level }}")
                put("device_class", "battery")
                put("unit_of_measurement", "%")
                put("state_class", "measurement")
            },
            config("binary_sensor", "charging") {
                put("name", "Charging")
                put("state_topic", t.battery)
                put("value_template", boolTemplate("charging"))
                put("payload_on", "ON")
                put("payload_off", "OFF")
                put("device_class", "battery_charging")
            },
            config("sensor", "charge_type") {
                put("name", "Charge type")
                put("state_topic", t.battery)
                put("value_template", "{{ value_json.charge_type }}")
                put("icon", "mdi:power-plug")
            },
            config("binary_sensor", "wifi") {
                put("name", "Wi-Fi")
                put("state_topic", t.wifi)
                put("value_template", boolTemplate("connected"))
                put("payload_on", "ON")
                put("payload_off", "OFF")
                put("device_class", "connectivity")
            },
            config("sensor", "ssid") {
                put("name", "SSID")
                put("state_topic", t.wifi)
                put("value_template", "{{ value_json.ssid }}")
                put("icon", "mdi:wifi")
            },
            config("sensor", "steps") {
                put("name", "Steps")
                put("state_topic", t.sensors)
                put("value_template", "{{ value_json.step_counter }}")
                put("state_class", "total_increasing")
                put("icon", "mdi:walk")
            },
            config("binary_sensor", "motion") {
                put("name", "Motion")
                put("state_topic", t.sensors)
                put("value_template", boolTemplate("motion_detected"))
                put("payload_on", "ON")
                put("payload_off", "OFF")
                put("device_class", "motion")
            },
            config("button", "find_phone") {
                put("name", "Find phone")
                put("command_topic", t.ping)
                put("payload_press", """{"seconds":30}""")
                put("icon", "mdi:bell-ring")
            },
            config("button", "take_photo") {
                put("name", "Take photo")
                put("command_topic", t.takePhoto)
                put("payload_press", """{"camera":"rear"}""")
                put("icon", "mdi:camera")
            },
            config("button", "locate") {
                put("name", "Update location")
                put("command_topic", t.getLocation)
                put("payload_press", "PRESS")
                put("icon", "mdi:crosshairs-gps")
            },
            config("button", "refresh") {
                put("name", "Refresh status")
                put("command_topic", t.getStatus)
                put("payload_press", "PRESS")
                put("icon", "mdi:refresh")
            },
        )
    }
}
