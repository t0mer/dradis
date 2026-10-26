package dev.tomerklein.dradis.commands

import android.content.Context
import dev.tomerklein.dradis.log.MqttLog
import dev.tomerklein.dradis.mqtt.Topics
import dev.tomerklein.dradis.settings.DradisSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSink(override val topics: Topics) : CommandSink {
    override val appContext: Context get() = error("unused in these tests")
    override val settings = DradisSettings()
    override val currentSsid: String? = null
    override val onWifi = false
    val published = mutableListOf<Pair<String, String>>()
    val infos = mutableListOf<String>()
    override fun publish(topic: String, payload: String, retain: Boolean) { published += topic to payload }
    override fun publish(topic: String, payload: ByteArray, retain: Boolean) {}
    override fun logInfo(message: String) { infos += message }
}

class CmdHandlerTest {
    private val topics = Topics("zanzito", "phone")
    private val sink = FakeSink(topics)
    private val log = MqttLog()
    private val calls = mutableListOf<String>()
    private val photoTopics = mutableListOf<String>()

    private val photo = object : CommandHandler {
        override fun handles(topic: String, topics: Topics) = false
        override suspend fun handle(topic: String, payload: String, sink: CommandSink) { photoTopics += topic }
    }

    private fun handler(fix: LocationPayload? = null) = CmdHandler(
        mqttLog = log,
        onHeartbeat = { calls += "heartbeat" },
        onReconnect = { calls += "reconnect" },
        onRestart = { calls += "restart" },
        photo = photo,
        stopAlarm = { calls += "stop_alarm" },
        locate = { fix },
    )

    @Test fun handlesOnlyCmdTopics() {
        assertTrue(handler().handles(topics.cmd("restart"), topics))
        assertFalse(handler().handles(topics.ping, topics))
    }

    @Test fun routesSimpleVerbs() = runTest {
        for (v in listOf("send_heartbeat", "reconnect", "restart", "stop_alarm")) {
            handler().handle(topics.cmd(v), "{}", sink)
        }
        assertEquals(listOf("heartbeat", "reconnect", "restart", "stop_alarm"), calls)
    }

    @Test fun takePictureDelegatesToPhotoTopic() = runTest {
        handler().handle(topics.cmd("take_picture"), """{"camera":"front"}""", sink)
        assertEquals(listOf(topics.takePhoto), photoTopics)
    }

    @Test fun blankPayloadIsIgnored() = runTest {
        handler().handle(topics.cmd("restart"), "  ", sink)
        assertTrue(calls.isEmpty())
    }

    @Test fun unknownVerbIsLoggedNoOp() = runTest {
        handler().handle(topics.cmd("nope"), "{}", sink)
        assertTrue(calls.isEmpty())
        assertTrue(sink.infos.any { "command not found" in it })
    }

    @Test fun sendLogsPublishesInboundAndOutboundSeparately() = runTest {
        log.inbound("in/topic", "a", 0L)
        log.outbound("out/topic", "b", 0L)
        handler().handle(topics.cmd("send_logs"), "{}", sink)
        val byTopic = sink.published.toMap()
        assertTrue(byTopic.getValue(topics.logInbound).contains("in/topic"))
        assertFalse(byTopic.getValue(topics.logInbound).contains("out/topic"))
        assertTrue(byTopic.getValue(topics.logOutbound).contains("out/topic"))
    }

    @Test fun repeatedSendLogsDoesNotEmbedPreviousDumps() = runTest {
        log.outbound("out/topic", "b", 0L)
        val h = handler()
        h.handle(topics.cmd("send_logs"), "{}", sink)
        // The service logs every published payload as an outbound entry.
        sink.published.forEach { (topic, body) -> log.outbound(topic, body, 1L) }
        sink.published.clear()
        h.handle(topics.cmd("send_logs"), "{}", sink)
        assertEquals(1, sink.published.toMap().getValue(topics.logOutbound).lines().size)
    }

    @Test fun sendHtmlLogsEmitsHtml() = runTest {
        log.inbound("in/<x>", "a", 0L)
        handler().handle(topics.cmd("send_html_logs"), "{}", sink)
        assertTrue(sink.published.toMap().getValue(topics.logInbound).contains("in/&lt;x&gt;"))
    }

    @Test fun setBookmarkLogsFixOrFailure() = runTest {
        handler(LocationPayload(1.5, 2.5, 3f, 4L)).handle(topics.cmd("set_bookmark"), "{}", sink)
        assertTrue(sink.infos.any { "1.5,2.5" in it })
        handler(null).handle(topics.cmd("set_bookmark"), "{}", sink)
        assertTrue(sink.infos.any { "location unavailable" in it })
    }
}
