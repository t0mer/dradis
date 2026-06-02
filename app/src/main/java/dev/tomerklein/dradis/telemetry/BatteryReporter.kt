package dev.tomerklein.dradis.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dev.tomerklein.dradis.commands.CommandSink
import dev.tomerklein.dradis.commands.DeviceInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds and publishes the `device_info` telemetry blob (CLAUDE.md §6.3, §9.5):
 * battery level, charging state and charge type. Also republishes on every
 * charging-state change while registered.
 */
class BatteryReporter(private val sink: CommandSink) {

    private val json = Json { encodeDefaults = true }
    private var receiver: BroadcastReceiver? = null

    /** Register for charging-state changes; publish a report on each. */
    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (sink.settings.telemetryEnabled) publish(intent)
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

    /** Publish a one-off report (used on connect and on `getstatus`). */
    fun publishNow() = publish(readBatteryIntent())

    private fun publish(batteryIntent: Intent?) {
        val info = build(batteryIntent)
        sink.publish(sink.topics.deviceInfo, json.encodeToString(info))
    }

    private fun readBatteryIntent(): Intent? =
        sink.appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun build(batteryIntent: Intent?): DeviceInfo {
        val context = sink.appContext
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

        val bm = context.getSystemService(BatteryManager::class.java)
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: batteryLevelFromIntent(batteryIntent)

        return DeviceInfo(
            time = System.currentTimeMillis() / 1000,
            deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.VERSION.RELEASE})",
            chargeType = chargeType,
            batteryCharging = charging,
            batteryLevel = level,
            currentForegroundApp = "DRADIS",
            screenLocked = isScreenLocked(),
        )
    }

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
