package dev.tomerklein.dradis.mqtt

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

private const val TAG = "dradis.locks"

/**
 * Holds a Wi-Fi lock + partial wake lock so the persistent MQTT connection
 * survives device sleep. When the screen turns off on battery, Android powers
 * down the Wi-Fi radio and suspends the CPU, so the ~45s keepalive PINGREQ isn't
 * delivered and the broker drops the link — it only reconnects when the screen
 * wakes the radio again. A find-my-phone / remote-control endpoint has to stay
 * reachable while the phone is asleep, so we keep the radio and CPU alive while
 * connected.
 *
 * The wake lock costs battery (the CPU can't deep-sleep), so it is gated by the
 * `keepAliveWhileLocked` setting. [WifiManager.WIFI_MODE_FULL_HIGH_PERF] is used
 * (not `FULL_LOW_LATENCY`, which is only active while the app is foreground) so
 * the radio stays awake from the background service.
 */
class ConnectionLocks(context: Context) {

    private val appContext = context.applicationContext
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    @Synchronized
    fun acquire() {
        if (wakeLock == null) {
            wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dradis:mqtt")?.apply {
                setReferenceCounted(false)
                runCatching { acquire() }
            }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION") // FULL_LOW_LATENCY only works in foreground; we run in a service
            val mode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wifi?.createWifiLock(mode, "dradis:mqtt")?.apply {
                setReferenceCounted(false)
                runCatching { acquire() }
            }
        }
        Log.i(TAG, "connection locks acquired (wake=${wakeLock?.isHeld} wifi=${wifiLock?.isHeld})")
    }

    @Synchronized
    fun release() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) runCatching { it.release() } }
        wifiLock = null
        Log.i(TAG, "connection locks released")
    }
}
