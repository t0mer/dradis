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

/** Inbound `say` payload — speaks the text via text-to-speech. */
@Serializable
data class SayCommand(
    val text: String = "",
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

/** Outbound `battery` — charge level and charging info. */
@Serializable
data class BatteryInfo(
    @SerialName("battery_level") val level: Int,
    val charging: Boolean,
    /** "AC" | "USB" | "Wireless" | "None". */
    @SerialName("charge_type") val chargeType: String,
)

/** Outbound `wifi` — Wi-Fi connection state and SSID. */
@Serializable
data class WifiInfo(
    val connected: Boolean,
    /** SSID name, or null when not connected / unreadable. */
    val ssid: String? = null,
)

/** Outbound `sensors` — step + motion sensor data. */
@Serializable
data class SensorsInfo(
    /** Cumulative steps since boot (TYPE_STEP_COUNTER), or null if unavailable. */
    @SerialName("step_counter") val stepCounter: Long? = null,
    /** Steps detected since the service started (TYPE_STEP_DETECTOR). */
    @SerialName("steps_detected") val stepsDetected: Long = 0,
    /** True if significant motion occurred since the last report. */
    @SerialName("motion_detected") val motionDetected: Boolean = false,
    val time: Long,
)

/** Outbound `device_info` — static device descriptor + screen-lock state. */
@Serializable
data class DeviceInfo(
    val time: Long,
    @SerialName("device_info") val deviceInfo: String,
    @SerialName("screen_locked") val screenLocked: Boolean,
)
