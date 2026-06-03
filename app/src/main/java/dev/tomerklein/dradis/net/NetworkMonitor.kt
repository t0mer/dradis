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

    @Volatile
    private var onWifi: Boolean = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh()
        }

        override fun onLost(network: Network) {
            // Re-evaluate the active network rather than blindly clearing — another
            // network (or the same Wi-Fi) may still be connected.
            refresh()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            update(caps)
            onChange()
        }
    }

    private fun refresh() {
        val active = cm.activeNetwork
        update(active?.let { cm.getNetworkCapabilities(it) })
        onChange()
    }

    private fun update(caps: NetworkCapabilities?) {
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        onWifi = isWifi
        if (isWifi) {
            // The SSID is not present in every capabilities callback. Update it
            // only when we actually read a name; keep the last-known one on a
            // transient null so we don't flap LAN -> WAN and drop a good link.
            val read = extractSsid(caps!!)
            if (read != null) ssid = read
            Log.i(TAG, "net update: wifi=true read=$read ssid=$ssid")
        } else {
            ssid = null
            Log.i(TAG, "net update: wifi=false ssid=null")
        }
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

    /** Force a re-read of the active network and its SSID, firing [onChange].
     *  Useful at startup or after location permission is granted / the app is
     *  foregrounded, when the SSID may not have been readable on the first pass. */
    fun reevaluate() = refresh()

    /** Current SSID, or null if not on Wi-Fi / unreadable (→ WAN). */
    fun currentSsid(): String? = ssid

    /** True when the active network is Wi-Fi (independent of SSID readability). */
    fun isWifi(): Boolean = onWifi

    private fun extractSsid(caps: NetworkCapabilities): String? {
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // transportInfo carries an unredacted WifiInfo only in live callbacks
            // (with location permission). Via getNetworkCapabilities() — or when a
            // VPN is the default network — the WifiInfo is absent or its SSID is
            // redacted to "<unknown ssid>". In those cases fall through to
            // WifiManager instead of returning null (which wrongly forces WAN).
            val fromCaps = (caps.transportInfo as? WifiInfo)?.let { normalize(it.ssid) }
            if (fromCaps != null) return fromCaps
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
