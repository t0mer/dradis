package dev.tomerklein.dradis.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFormatterTest {
    private val entries = listOf(
        LogEntry(0L, Direction.IN, "a/b", "hello"),
        LogEntry(1000L, Direction.OUT, "a/<c>", "x & y"),
    )

    @Test fun textHasOneLinePerEntry() {
        assertEquals(
            "1970-01-01T00:00:00Z a/b hello\n1970-01-01T00:00:01Z a/<c> x & y",
            LogFormatter.toText(entries),
        )
    }

    @Test fun textOfEmptyListIsEmpty() {
        assertEquals("", LogFormatter.toText(emptyList()))
    }

    @Test fun htmlEscapesUntrustedFields() {
        val html = LogFormatter.toHtml(entries, "t<i>")
        assertTrue(html.contains("a/&lt;c&gt;"))
        assertTrue(html.contains("x &amp; y"))
        assertTrue(html.contains("<title>t&lt;i&gt;</title>"))
        assertFalse(html.contains("<c>"))
    }

    @Test fun dumpRedactsPhoneNumbers() {
        val text = LogFormatter.toText(listOf(LogEntry(0L, Direction.OUT, "t", "to 972501234567 ok")))
        assertTrue(text.contains("•••567"))
        assertFalse(text.contains("972501234567"))
    }

    @Test fun dumpCapsLongFields() {
        val big = "x".repeat(10_000)
        val line = LogFormatter.toText(listOf(LogEntry(0L, Direction.OUT, "t", big)))
        assertTrue(line.length < LogFormatter.MAX_FIELD_CHARS + 100)
        assertTrue(line.endsWith("…"))
    }

    @Test fun dumpFlattensLineBreaksSoEntriesCannotBeForged() {
        val forged = "x\n2026-10-26T10:00:00Z fake/topic {\"ok\":true}"
        val text = LogFormatter.toText(listOf(LogEntry(0L, Direction.IN, "t", forged)))
        assertEquals(1, text.lines().size)
    }
}
