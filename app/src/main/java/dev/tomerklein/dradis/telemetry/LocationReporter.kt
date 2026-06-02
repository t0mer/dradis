package dev.tomerklein.dradis.telemetry

import dev.tomerklein.dradis.commands.CommandSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Publishes location on a user-configured interval when enabled (CLAUDE.md §9.3).
 * [restart] is called whenever settings change so interval/toggle edits apply.
 */
class LocationReporter(
    private val sink: CommandSink,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun restart() {
        stop()
        val s = sink.settings
        if (!s.locationPublishEnabled || !s.locationEnabled) return
        sink.logInfo("Periodic location ON — every ${s.locationIntervalSeconds.coerceAtLeast(15)}s")
        job = scope.launch {
            while (isActive) {
                val cur = sink.settings
                if (cur.locationPublishEnabled && cur.locationEnabled) {
                    sink.logInfo("Auto-location tick")
                    LocationPublisher.publishCurrent(sink)
                }
                val interval = cur.locationIntervalSeconds.coerceAtLeast(15)
                delay(interval * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
