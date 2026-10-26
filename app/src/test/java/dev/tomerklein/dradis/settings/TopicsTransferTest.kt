package dev.tomerklein.dradis.settings

import dev.tomerklein.dradis.mqtt.Topics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsTransferTest {
    private val builtIn = Topics("dradis", "phone").let { it.inboundTopics() + it.outboundTopics() }
    private fun t(id: String, filter: String, name: String = "n$id") = CustomTopic(id = id, name = name, filter = filter)

    private fun ok(r: TopicsTransfer.Result) = r as TopicsTransfer.Result.Ok
    private fun err(r: TopicsTransfer.Result) = (r as TopicsTransfer.Result.Error).message

    @Test fun exportThenImportRoundTripsWithFreshIds() {
        val text = TopicsTransfer.export(listOf(t("a", "x/a"), t("b", "x/b").copy(type = CustomTopicType.EXTRACTED, extractor = "$.v")))
        val r = ok(TopicsTransfer.import(text, emptyList(), builtIn))
        assertEquals(2, r.added)
        assertEquals(listOf("x/a", "x/b"), r.merged.map { it.filter })
        assertEquals(CustomTopicType.EXTRACTED, r.merged[1].type)
        assertTrue(r.merged.none { it.id == "a" || it.id == "b" })
        assertTrue("imports start disabled", r.merged.none { it.enabled })
    }

    @Test fun importIsAppendedAndNeverOverwritesExisting() {
        val existing = listOf(t("a", "x/a"))
        val r = ok(TopicsTransfer.import(TopicsTransfer.export(listOf(t("a", "x/other"))), existing, builtIn))
        assertEquals(2, r.merged.size)
        assertEquals("a", r.merged[0].id)
        assertNotEquals("a", r.merged[1].id)
    }

    @Test fun acceptsBareArray() {
        val r = ok(TopicsTransfer.import("""[{"id":"1","name":"n","filter":"x/y"}]""", emptyList(), builtIn))
        assertEquals(1, r.added)
    }

    @Test fun rejectsGarbageAndEmpty() {
        assertEquals("Not a valid custom topics file", err(TopicsTransfer.import("{\"topics\": ", emptyList(), builtIn)))
        assertEquals("Not a custom topics file", err(TopicsTransfer.import("42", emptyList(), builtIn)))
        assertEquals("The file contains no topics", err(TopicsTransfer.import("[]", emptyList(), builtIn)))
    }

    @Test fun rejectsWholeImportWhenOneTopicIsInvalid() {
        val text = TopicsTransfer.export(listOf(t("a", "x/ok"), t("b", "dradis/phone/cmd/restart")))
        assertTrue(TopicsTransfer.import(text, emptyList(), builtIn) is TopicsTransfer.Result.Error)
    }

    @Test fun rejectsOverlapWithExistingTopics() {
        val r = TopicsTransfer.import(TopicsTransfer.export(listOf(t("z", "x/#"))), listOf(t("a", "x/a")), builtIn)
        assertTrue(r is TopicsTransfer.Result.Error)
    }

    @Test fun enforcesTotalAndFileSizeCaps() {
        val many = (1..CustomTopicValidator.MAX_TOPICS).map { t("e$it", "e/$it") }
        assertEquals("At most 100 custom topics", err(TopicsTransfer.import(TopicsTransfer.export(listOf(t("z", "z/z"))), many, builtIn)))
        assertEquals("File is too large", err(TopicsTransfer.import("x".repeat(TopicsTransfer.MAX_FILE_BYTES + 1), emptyList(), builtIn)))
    }
}
