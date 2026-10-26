package dev.tomerklein.dradis.telemetry

import dev.tomerklein.dradis.commands.BatteryInfo
import dev.tomerklein.dradis.commands.CommandSink
import dev.tomerklein.dradis.commands.LocationPayload
import dev.tomerklein.dradis.commands.WifiInfo

/** [DeviceValues] backed by the real telemetry reporter and location publisher. */
class AndroidDeviceValues(
    private val telemetry: TelemetryReporter,
    private val sink: CommandSink,
) : DeviceValues {
    override fun battery(): BatteryInfo = telemetry.currentBattery()
    override fun wifi(): WifiInfo = telemetry.currentWifi()
    override suspend fun location(): LocationPayload? = LocationPublisher.currentPayload(sink)
}
