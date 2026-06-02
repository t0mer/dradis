package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.mqtt.Topics

/**
 * On `getstatus`, forces an immediate telemetry report (CLAUDE.md §6.1).
 * The actual report is produced by the service-owned reporter via [onReport].
 */
class GetStatusHandler(private val onReport: () -> Unit) : CommandHandler {

    override fun handles(topic: String, topics: Topics): Boolean =
        topic == topics.getStatus

    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        if (!sink.settings.telemetryEnabled) {
            sink.logInfo("Telemetry disabled in settings; ignoring")
            return
        }
        onReport()
    }
}
