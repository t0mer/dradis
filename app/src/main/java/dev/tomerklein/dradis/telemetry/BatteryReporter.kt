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
import dev.tomerklein.dradis.commands.LocationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds and publishes the `device_info` telemetry blob (CLAUDE.md §6.3, §9.5):
 * battery level, charging state and charge type, plus the connected Wi-Fi SSID
 * and the latest location fix. Also republishes on every charging-state change.
 *
 * Reporting is asynchronous (it fetches a location fix), so it runs on [scope].
 */
class BatteryReporter(
    private val sink: CommandSink,
    private val scope: CoroutineScope,
) {
    private val json = Json { encodeDefaults = true }
    private var receiver: BroadcastReceiver? = null

    /** Register for charging-state changes; publish a report on each. */
    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (sink.settings.telemetryEnabled) report()
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

    /** Fire a telemetry report (used on connect, on `getstatus`, and on
     *  charging change). Fetches SSID + a fresh location, then publishes. */
    fun report() {
        scope.launch { publishReport(prefetchedLocation = null, usePrefetched = false) }
    }

    /**
     * Publish a telemetry report. When [usePrefetched] is true, [prefetchedLocation]
     * is used as-is (so the periodic reporter can fetch one fix for both the
     * telemetry and the location topic); otherwise a fresh fix is fetched here.
     */
    suspend fun publishReport(prefetchedLocation: LocationPayload?, usePrefetched: Boolean) {
        val location = when {
            usePrefetched -> prefetchedLocation
            sink.settings.locationEnabled ->
                runCatching { LocationPublisher.currentPayload(sink) }.getOrNull()
            else -> null
        }
        val info = build(readBatteryIntent(), sink.currentSsid, location)
        sink.publish(sink.topics.deviceInfo, json.encodeToString(info))
    }

    private fun readBatteryIntent(): Intent? =
        sink.appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun build(
        batteryIntent: Intent?,
        ssid: String?,
        location: LocationPayload?,
    ): DeviceInfo {
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
            wifiSsid = ssid,
            location = location,
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
