package dev.tomerklein.dradis.telemetry

import android.content.Context
import dev.tomerklein.dradis.commands.BatteryInfo
import dev.tomerklein.dradis.commands.CommandSink
import dev.tomerklein.dradis.commands.LocationPayload
import dev.tomerklein.dradis.commands.WifiInfo
import dev.tomerklein.dradis.mqtt.Topics
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.settings.OutboundSource
import dev.tomerklein.dradis.settings.OutboundTopic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class Sink(override var settings: DradisSettings = DradisSettings()) : CommandSink {
    override val appContext: Context get() = error("unused")
    override val topics = Topics("dradis", "phone")
    override val currentSsid: String? = null
    override val onWifi = false
    val out = mutableListOf<Triple<String, String, Boolean>>()
    override fun publish(topic: String, payload: String, retain: Boolean) { out += Triple(topic, payload, retain) }
    override fun publish(topic: String, payload: ByteArray, retain: Boolean) {}
    override fun logInfo(message: String) {}
}

private class Values(var ssid: String? = "Home", var fix: LocationPayload? = LocationPayload(1.5, 2.5, 3f, 4L)) : DeviceValues {
    var locationReads = 0
    var batteryReads = 0
    override fun battery(): BatteryInfo { batteryReads++; return BatteryInfo(87, true, "USB") }
    override fun wifi() = WifiInfo(ssid != null, ssid)
    override suspend fun location(): LocationPayload? { locationReads++; return fix }
}

class CustomOutboundPublisherTest {
    private val sink = Sink()
    private val values = Values()
    private val pub = CustomOutboundPublisher(sink, values)
    private fun t(id: String, topic: String, src: OutboundSource, retain: Boolean = false, enabled: Boolean = true) =
        OutboundTopic(id, topic, src, retain, enabled)

    @Test fun publishesEachSourceWithRetainFlag() = runTest {
        pub.publishAll(
            listOf(
                t("1", "x/level", OutboundSource.BATTERY_LEVEL, retain = true),
                t("2", "x/charging", OutboundSource.CHARGING),
                t("3", "x/ssid", OutboundSource.WIFI_SSID),
            ),
        )
        assertEquals(
            listOf(Triple("x/level", "87", true), Triple("x/charging", "true", false), Triple("x/ssid", "Home", false)),
            sink.out,
        )
    }

    @Test fun skipsDisabledEntries() = runTest {
        pub.publishAll(listOf(t("1", "x/a", OutboundSource.BATTERY_LEVEL, enabled = false)))
        assertTrue(sink.out.isEmpty())
    }

    @Test fun neverPublishesAnEmptyPayload() = runTest {
        values.ssid = null
        pub.publishAll(listOf(t("1", "x/ssid", OutboundSource.WIFI_SSID, retain = true)))
        assertEquals("none", sink.out.single().second)
        values.ssid = "  "
        sink.out.clear()
        pub.publishAll(listOf(t("1", "x/ssid", OutboundSource.WIFI_SSID)))
        assertEquals("none", sink.out.single().second)
    }

    @Test fun telemetrySwitchGatesBatteryAndWifi() = runTest {
        sink.settings = sink.settings.copy(telemetryEnabled = false)
        pub.publishAll(listOf(t("1", "x/a", OutboundSource.BATTERY_LEVEL), t("2", "x/b", OutboundSource.WIFI_SSID)))
        assertTrue(sink.out.isEmpty())
    }

    @Test fun locationHonoursSettingAndNeedsAFix() = runTest {
        sink.settings = sink.settings.copy(locationEnabled = false)
        pub.publishAll(listOf(t("1", "x/loc", OutboundSource.LOCATION)))
        assertTrue(sink.out.isEmpty())
        assertEquals(0, values.locationReads)

        sink.settings = sink.settings.copy(locationEnabled = true)
        values.fix = null
        pub.publishAll(listOf(t("1", "x/loc", OutboundSource.LOCATION)))
        assertTrue(sink.out.isEmpty())

        values.fix = LocationPayload(1.5, 2.5, 3f, 4L)
        pub.publishAll(listOf(t("1", "x/loc", OutboundSource.LOCATION)))
        assertTrue(sink.out.single().second.contains("\"latitude\":1.5"))
    }

    @Test fun readsEachSourceOncePerRound() = runTest {
        pub.publishAll(
            listOf(
                t("1", "x/a", OutboundSource.LOCATION), t("2", "x/b", OutboundSource.LOCATION),
                t("3", "x/c", OutboundSource.BATTERY_LEVEL), t("4", "x/d", OutboundSource.CHARGING),
            ),
        )
        assertEquals(1, values.locationReads)
        assertEquals(1, values.batteryReads)
        assertEquals(4, sink.out.size)
    }
}
