package dev.tomerklein.dradis.mqtt

import dev.tomerklein.dradis.settings.BrokerConfig
import dev.tomerklein.dradis.settings.DradisSettings

/**
 * Decides LAN vs WAN broker from the current SSID (CLAUDE.md §7):
 * a home SSID → LAN broker; everything else (mobile data / unknown / foreign
 * Wi-Fi) → WAN broker.
 *
 * If only **one** broker is configured, it is used for both networks — so a
 * user who fills in a single broker doesn't need to duplicate it into both the
 * LAN and WAN slots.
 */
object BrokerSelector {

    data class Selection(
        val kind: BrokerKind,
        val config: BrokerConfig,
        val ssid: String?,
    )

    fun select(settings: DradisSettings, ssid: String?): Selection {
        val onHomeWifi = ssid != null && settings.homeSsids.any { it.trim() == ssid }

        // Broker preferred for the current network, plus the other as fallback.
        val primaryKind = if (onHomeWifi) BrokerKind.LAN else BrokerKind.WAN
        val primary = if (onHomeWifi) settings.lanBroker else settings.wanBroker
        val secondaryKind = if (onHomeWifi) BrokerKind.WAN else BrokerKind.LAN
        val secondary = if (onHomeWifi) settings.wanBroker else settings.lanBroker

        return when {
            primary.isConfigured -> Selection(primaryKind, primary, ssid)
            // Only the other broker is configured → use it for both networks.
            secondary.isConfigured -> Selection(secondaryKind, secondary, ssid)
            // Neither configured → report the preferred one (status shows unconfigured).
            else -> Selection(primaryKind, primary, ssid)
        }
    }
}
