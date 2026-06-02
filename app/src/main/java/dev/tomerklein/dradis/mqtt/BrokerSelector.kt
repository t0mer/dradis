package dev.tomerklein.dradis.mqtt

import dev.tomerklein.dradis.settings.BrokerConfig
import dev.tomerklein.dradis.settings.DradisSettings

/**
 * Decides LAN vs WAN broker from the current SSID (CLAUDE.md §7):
 * a home SSID → LAN broker; everything else (mobile data / unknown / foreign
 * Wi-Fi) → WAN broker.
 */
object BrokerSelector {

    data class Selection(
        val kind: BrokerKind,
        val config: BrokerConfig,
        val ssid: String?,
    )

    fun select(settings: DradisSettings, ssid: String?): Selection {
        val onHomeWifi = ssid != null && settings.homeSsids.any { it.trim() == ssid }
        return if (onHomeWifi) {
            Selection(BrokerKind.LAN, settings.lanBroker, ssid)
        } else {
            Selection(BrokerKind.WAN, settings.wanBroker, ssid)
        }
    }
}
