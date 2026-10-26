package dev.tomerklein.dradis.settings

import dev.tomerklein.dradis.mqtt.Topics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OutboundTopicValidatorTest {
    private val builtIn = Topics("dradis", "phone").let { it.inboundTopics() + it.outboundTopics() }
    private fun o(id: String, topic: String) = OutboundTopic(id, topic, OutboundSource.BATTERY_LEVEL)
    private fun err(vararg l: OutboundTopic, custom: List<String> = emptyList()) =
        OutboundTopicValidator.validateAll(l.toList(), builtIn, custom)

    @Test fun acceptsPlainTopic() = assertNull(err(o("a", "home/phone/battery_level")))
    @Test fun rejectsWildcardsAndSystemTopics() {
        assertNotNull(err(o("a", "home/+/x")))
        assertNotNull(err(o("a", "home/#")))
        assertNotNull(err(o("a", "\$SYS/x")))
    }
    @Test fun rejectsEmptyLevelsAndBlank() {
        for (bad in listOf("", "/a", "a/", "a//b")) assertNotNull(bad, err(o("a", bad)))
    }
    @Test fun rejectsTopicsTheAppUsesItself() {
        assertNotNull(err(o("a", "dradis/phone/battery")))
        assertNotNull(err(o("a", "dradis/phone/cmd/restart")))
        assertNotNull(err(o("a", "dradis/phone/ping")))
    }
    @Test fun rejectsTopicsMatchingACustomInboundFilter() {
        assertNotNull(err(o("a", "x/y/z"), custom = listOf("x/#")))
        assertNull(err(o("a", "y/z"), custom = listOf("x/#")))
    }
    @Test fun rejectsDuplicatesAndBadIds() {
        assertEquals("Duplicate ids", err(o("a", "x/1"), o("a", "x/2")))
        assertNotNull(err(o("a", "x/1"), o("b", "x/1")))
        assertNotNull(err(o("bad id", "x/1")))
    }
    @Test fun capsCount() {
        val many = (1..OutboundTopicValidator.MAX_TOPICS + 1).map { o("i$it", "x/$it") }
        assertEquals("At most 50 outbound topics", OutboundTopicValidator.validateAll(many, builtIn, emptyList()))
    }
}
