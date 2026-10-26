package dev.tomerklein.dradis.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadExtractorsTest {
    private val doc = """{"a":{"b":[10,{"c":"x y"}],"n":null},"k.d":true,"s":"hi"}"""

    @Test fun jsonPathKeysAndIndexes() {
        assertEquals("hi", PayloadExtractors.extract(doc, "$.s"))
        assertEquals("10", PayloadExtractors.extract(doc, "$.a.b[0]"))
        assertEquals("x y", PayloadExtractors.extract(doc, "$.a.b[1].c"))
        assertEquals("true", PayloadExtractors.extract(doc, "$['k.d']"))
        assertEquals("""{"c":"x y"}""", PayloadExtractors.extract(doc, "$.a.b[1]"))
    }

    @Test fun jsonPathMissesReturnNull() {
        assertNull(PayloadExtractors.extract(doc, "$.nope"))
        assertNull(PayloadExtractors.extract(doc, "$.a.b[9]"))
        assertNull(PayloadExtractors.extract(doc, "$.a.n"))
        assertNull(PayloadExtractors.extract(doc, "$.s.x"))
        assertNull(PayloadExtractors.extract("not json", "$.a"))
    }

    @Test fun malformedJsonPathReturnsNull() {
        for (p in listOf("$.", "$[", "$[x]", "$[-1]", "\$a", "\$..a")) assertNull(p, PayloadExtractors.extract(doc, p))
    }

    @Test fun regexUsesGroupOneOrWholeMatch() {
        assertEquals("42", PayloadExtractors.extract("temp=42C", "temp=(\\d+)"))
        assertEquals("temp=42", PayloadExtractors.extract("temp=42C", "temp=\\d+"))
        assertNull(PayloadExtractors.extract("nothing", "\\d+"))
        assertNull(PayloadExtractors.extract("x", "("))
    }

    @Test fun inputIsCapped() {
        val big = "a".repeat(PayloadExtractors.MAX_INPUT) + "TAIL"
        assertNull(PayloadExtractors.extract(big, "TAIL"))
    }

    @Test fun keyValueSplitsOnFirstSeparator() {
        assertEquals("door" to "open: now", PayloadExtractors.keyValue(" door : open: now ", ":", "t"))
        assertEquals("t" to "plain", PayloadExtractors.keyValue(" plain ", ":", "t"))
        assertEquals("t" to "v", PayloadExtractors.keyValue(": v", ":", "t"))
        assertEquals("k" to "v", PayloadExtractors.keyValue("k=>v", "=>", "t"))
    }

    @Test fun catastrophicRegexIsAbortedWithinTheBudget() {
        val evil = "a".repeat(5_000) + "!"
        val start = System.nanoTime()
        assertNull(PayloadExtractors.extract(evil, "(a+)+$"))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took ${ms}ms", ms < 2_000)
    }

    @Test fun optionalGroupThatDidNotCaptureIsNoValue() {
        assertNull(PayloadExtractors.extract("temp=x", "temp=(\\d+)?"))
        assertEquals("temp=21", PayloadExtractors.extract("temp=21", "temp=\\d+"))
    }

    @Test fun repeatedExtractionUsesTheSamePattern() {
        repeat(3) { assertEquals("1", PayloadExtractors.extract("n=1", "n=(\\d)")) }
    }
}
