package dev.tomerklein.dradis.telemetry

import dev.tomerklein.dradis.commands.CommandSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Publishes periodic updates at the user-configured update interval
 * (CLAUDE.md §9.3). Each tick publishes both the location fix and the
 * telemetry blob, honouring the per-feature toggles. The location fix is
 * fetched once per tick and reused for both. [restart] is called whenever
 * settings change so interval/toggle edits apply immediately.
 */
class PeriodicReporter(
    private val sink: CommandSink,
    private val scope: CoroutineScope,
    private val batteryReporter: BatteryReporter,
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
                    val location = if (cur.locationEnabled) {
                        runCatching { LocationPublisher.currentPayload(sink) }.getOrNull()
                    } else {
                        null
                    }
                    if (cur.locationEnabled && location != null) {
                        LocationPublisher.publishPayload(sink, location)
                    }
                    if (cur.telemetryEnabled) {
                        batteryReporter.publishReport(location, usePrefetched = true)
                    }
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
