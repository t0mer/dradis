package dev.tomerklein.dradis.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dev.tomerklein.dradis.commands.BatteryInfo
import dev.tomerklein.dradis.commands.CommandSink
import dev.tomerklein.dradis.commands.DeviceInfo
import dev.tomerklein.dradis.commands.WifiInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Publishes device telemetry split across topics (CLAUDE.md §6.2):
 * - `battery`     → level, charging flag, charge type
 * - `wifi`        → connection state + SSID
 * - `device_info` → device descriptor + screen-lock state
 *
 * Location is published separately (the `location` topic only). Battery is
 * also republished on every charging-state change while registered.
 */
class TelemetryReporter(private val sink: CommandSink) {

    private val json = Json { encodeDefaults = true }
    private var receiver: BroadcastReceiver? = null

    /** Register for charging-state changes; publish a report on each. */
    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (sink.settings.telemetryEnabled) publishAll()
            }
        }
        receiver = r
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        sink.appContext.registerReceiver(r, filter)
    }

    fun stop() {
        receiver?.let { runCatching { sink.appContext.unregisterReceiver(it) } }
        receiver = null
    }

    /** Publish battery, wifi and device_info. Caller gates on telemetryEnabled. */
    fun publishAll() {
        val t = sink.topics
        sink.publish(t.battery, json.encodeToString(buildBattery(readBatteryIntent())))
        sink.publish(t.wifi, json.encodeToString(buildWifi()))
        sink.publish(t.deviceInfo, json.encodeToString(buildDeviceInfo()))
    }

    private fun readBatteryIntent(): Intent? =
        sink.appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun buildBattery(batteryIntent: Intent?): BatteryInfo {
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargeType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }

        val bm = sink.appContext.getSystemService(BatteryManager::class.java)
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: batteryLevelFromIntent(batteryIntent)

        return BatteryInfo(level = level, charging = charging, chargeType = chargeType)
    }

    private fun buildWifi(): WifiInfo =
        WifiInfo(connected = sink.onWifi, ssid = sink.currentSsid)

    private fun buildDeviceInfo(): DeviceInfo = DeviceInfo(
        time = System.currentTimeMillis() / 1000,
        deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.VERSION.RELEASE})",
        currentForegroundApp = "DRADIS",
        screenLocked = isScreenLocked(),
    )

    private fun batteryLevelFromIntent(intent: Intent?): Int {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private fun isScreenLocked(): Boolean {
        val pm = sink.appContext.getSystemService(PowerManager::class.java)
        val interactive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm?.isInteractive ?: true
        } else true
        return !interactive
    }
}
