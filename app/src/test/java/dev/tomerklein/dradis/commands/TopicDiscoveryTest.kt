package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.mqtt.DuplicateGuard
import dev.tomerklein.dradis.settings.CustomTopicType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicDiscoveryTest {
    private var now = 0L
    private val d = TopicDiscovery { now }

    private fun rec(topic: String, payload: String = "x", retained: Boolean = false) =
        d.record(topic, payload.toByteArray(), retained)

    @Test fun nothingRecordedWithoutSession() {
        rec("a/b")
        assertTrue(d.topics.value.isEmpty())
    }

    @Test fun startValidatesFilterAndDuration() {
        assertNotNull(d.start("a/#/b", 10))
        assertNotNull(d.start("a/#", 0))
        assertNotNull(d.start("a/#", TopicDiscovery.MAX_SECONDS + 1))
        assertNull(d.start("a/#", 10))
    }

    @Test fun recordsOnlyMatchingTopicsAndCounts() {
        d.start("home/#", 60)
        rec("home/a"); rec("home/a"); rec("other/b")
        assertEquals(listOf("home/a"), d.topics.value.map { it.topic })
        assertEquals(2, d.topics.value.single().count)
    }

    @Test fun stopsRecordingAfterDeadline() {
        d.start("#", 10)
        now = 10_000
        rec("a")
        assertTrue(d.topics.value.isEmpty())
        assertFalse(d.isActive)
    }

    @Test fun capsNumberOfTopics() {
        d.start("#", 60)
        repeat(TopicDiscovery.MAX_TOPICS + 20) { rec("t/$it") }
        assertEquals(TopicDiscovery.MAX_TOPICS, d.topics.value.size)
    }

    @Test fun ignoresSystemTopicsAndOverlongTopics() {
        d.start("#", 60)
        rec("\$SYS/broker"); rec("a/" + "x".repeat(300))
        assertTrue(d.topics.value.isEmpty())
    }

    @Test fun startClearsPreviousResults() {
        d.start("#", 60); rec("a")
        d.start("#", 60)
        assertTrue(d.topics.value.isEmpty())
    }

    @Test fun previewRedactsFlattensAndCaps() {
        assertEquals("call •••567 now", TopicDiscovery.preview("call 972501234567\nnow".toByteArray()))
        assertTrue(TopicDiscovery.preview("y".repeat(500).toByteArray()).length <= 81)
    }

    @Test fun binaryPayloadShowsOnlySize() {
        assertEquals("<4 bytes>", TopicDiscovery.preview(byteArrayOf(0, 1, 2, 3)))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2)
        assertEquals("<6 bytes image>", TopicDiscovery.preview(jpeg))
    }

    @Test fun suggestsTypeFromPayload() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals(CustomTopicType.PICTURE, TopicDiscovery.suggest(jpeg))
        assertEquals(CustomTopicType.EXTRACTED, TopicDiscovery.suggest("""{"t":1}""".toByteArray()))
        assertEquals(CustomTopicType.TEXT, TopicDiscovery.suggest("hello".toByteArray()))
        assertEquals(CustomTopicType.TEXT, TopicDiscovery.suggest("[1,2]".toByteArray()))
    }

    @Test fun duplicateGuardDropsBackToBackIdenticalOnly() {
        var t = 0L
        val g = DuplicateGuard(250) { t }
        assertFalse(g.isDuplicate("a", byteArrayOf(1)))
        t = 100
        assertTrue(g.isDuplicate("a", byteArrayOf(1)))
        t = 500
        assertFalse(g.isDuplicate("a", byteArrayOf(1)))
        assertFalse(g.isDuplicate("a", byteArrayOf(2)))
    }
}
