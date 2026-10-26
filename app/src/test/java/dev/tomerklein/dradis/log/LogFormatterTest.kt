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
}
