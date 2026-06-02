package dev.tomerklein.dradis.commands

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Inbound `sendsms` payload (preferred JSON form). */
@Serializable
data class SmsCommand(
    val phone: String = "",
    val text: String = "",
)

/** Outbound `sendsms/result`. */
@Serializable
data class SmsResult(
    val phone: String,
    val ok: Boolean,
    val error: String? = null,
)

/** Outbound `location`. */
@Serializable
data class LocationPayload(
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val time: Long,
)

/** Inbound `ping` payload (optional duration). */
@Serializable
data class PingCommand(
    val seconds: Int = 30,
)

/** Inbound `takephoto` payload. */
@Serializable
data class PhotoCommand(
    val camera: String = "rear",
)

/** Inbound `notify` payload — pushes a notification to the device's shade. */
@Serializable
data class NotifyCommand(
    val title: String = "DRADIS",
    val text: String = "",
    /** Optional stable id so a later notify with the same id replaces it. */
    val id: Int? = null,
)

/** Outbound `photo` envelope (base64 JPEG). */
@Serializable
data class PhotoPayload(
    val camera: String,
    val time: Long,
    @SerialName("jpeg_b64") val jpegB64: String,
)

/**
 * Outbound `device_info` — field names reproduce the legacy Zanzito shape so
 * existing dashboards keep working (CLAUDE.md §6.3).
 */
@Serializable
data class DeviceInfo(
    val time: Long,
    @SerialName("device_info") val deviceInfo: String,
    @SerialName("charge_type") val chargeType: String,
    @SerialName("battery_charging") val batteryCharging: Boolean,
    @SerialName("battery_level") val batteryLevel: Int,
    @SerialName("current_foreground_app") val currentForegroundApp: String,
    @SerialName("screen_locked") val screenLocked: Boolean,
    /** Connected Wi-Fi SSID, or null when not on Wi-Fi (or unreadable). */
    @SerialName("wifi_ssid") val wifiSsid: String? = null,
    /** Latest location fix, or null when unavailable/disabled. */
    val location: LocationPayload? = null,
)
