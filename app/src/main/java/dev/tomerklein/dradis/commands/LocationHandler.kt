package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.mqtt.Topics
import dev.tomerklein.dradis.telemetry.LocationPublisher

/** On `getlocation`, fetches a fresh fix and publishes it (CLAUDE.md §9.3). */
class LocationHandler : CommandHandler {

    override fun handles(topic: String, topics: Topics): Boolean =
        topic == topics.getLocation

    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        if (!sink.settings.locationEnabled) {
            sink.logInfo("Location disabled in settings; ignoring")
            return
        }
        LocationPublisher.publishCurrent(sink)
    }
}
