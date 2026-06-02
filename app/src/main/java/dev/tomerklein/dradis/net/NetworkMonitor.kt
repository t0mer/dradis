package dev.tomerklein.dradis.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

private const val TAG = "dradis.net"

/**
 * Observes connectivity changes and resolves the current Wi-Fi SSID
 * (CLAUDE.md §7). SSID reading needs location permission + location services on;
 * when unavailable the SSID is reported as null → caller treats it as WAN.
 */
class NetworkMonitor(
    context: Context,
    private val onChange: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)

    @Volatile
    private var ssid: String? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh()
        }

        override fun onLost(network: Network) {
            ssid = null
            onChange()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            ssid = extractSsid(caps)
            onChange()
        }
    }

    private fun refresh() {
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        ssid = caps?.let { extractSsid(it) }
        onChange()
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onFailure { Log.e(TAG, "registerNetworkCallback failed", it) }
        refresh()
    }

    fun stop() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    /** Current SSID, or null if not on Wi-Fi / unreadable (→ WAN). */
    fun currentSsid(): String? = ssid

    private fun extractSsid(caps: NetworkCapabilities): String? {
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val info = caps.transportInfo as? WifiInfo
            if (info != null) return normalize(info.ssid)
        }
        return legacySsid()
    }

    @Suppress("DEPRECATION")
    private fun legacySsid(): String? {
        val info = wifi?.connectionInfo ?: return null
        return normalize(info.ssid)
    }

    /** Strip surrounding quotes; map placeholders to null (CLAUDE.md §7). */
    private fun normalize(raw: String?): String? {
        if (raw == null) return null
        val s = raw.trim('"').trim()
        if (s.isEmpty() || s.equals("<unknown ssid>", ignoreCase = true) || s == "0x") return null
        return s
    }
}
