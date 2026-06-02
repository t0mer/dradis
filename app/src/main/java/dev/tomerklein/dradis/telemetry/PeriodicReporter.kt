package dev.tomerklein.dradis.telemetry

import dev.tomerklein.dradis.commands.CommandSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Publishes periodic updates at the user-configured update interval
 * (CLAUDE.md §9.3). Each tick publishes location (its own topic) and telemetry
 * (battery, wifi, device_info), honouring the per-feature toggles. [restart] is
 * called whenever settings change so interval/toggle edits apply immediately.
 */
class PeriodicReporter(
    private val sink: CommandSink,
    private val scope: CoroutineScope,
    private val telemetryReporter: TelemetryReporter,
    private val sensorReporter: SensorReporter,
) {
    private var job: Job? = null

    fun restart() {
        stop()
        val s = sink.settings
        if (!s.periodicUpdatesEnabled) return
        sink.logInfo("Periodic updates ON — every ${s.updateIntervalSeconds.coerceAtLeast(15)}s")
        job = scope.launch {
            while (isActive) {
                val cur = sink.settings
                if (cur.periodicUpdatesEnabled) {
                    if (cur.locationEnabled) LocationPublisher.publishCurrent(sink)
                    if (cur.telemetryEnabled) telemetryReporter.publishAll()
                    if (cur.sensorsEnabled) sensorReporter.publish()
                }
                val interval = cur.updateIntervalSeconds.coerceAtLeast(15)
                delay(interval * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
