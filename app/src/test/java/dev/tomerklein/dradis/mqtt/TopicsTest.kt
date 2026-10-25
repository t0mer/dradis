package dev.tomerklein.dradis.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsTest {
    private val t = Topics("zanzito", "phone")

    @Test fun cmdBuildsVerbTopic() {
        assertEquals("zanzito/phone/cmd/restart", t.cmd("restart"))
    }

    @Test fun cmdVerbExtractsVerb() {
        assertEquals("take_picture", t.cmdVerb("zanzito/phone/cmd/take_picture"))
    }

    @Test fun cmdVerbRejectsOtherTopics() {
        assertNull(t.cmdVerb("zanzito/phone/ping"))
        assertNull(t.cmdVerb("zanzito/phone/cmd/"))
        assertNull(t.cmdVerb("zanzito/phone/cmd/a/b"))
        assertNull(t.cmdVerb("zanzito/other/cmd/restart"))
    }

    @Test fun inboundIncludesCmdWildcard() {
        assertTrue("zanzito/phone/cmd/+" in t.inboundTopics())
    }

    @Test fun logTopics() {
        assertEquals("zanzito/phone/log_inbound", t.logInbound)
        assertEquals("zanzito/phone/log_outbound", t.logOutbound)
    }
}
