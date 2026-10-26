package dev.tomerklein.dradis.mqtt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicFilterTest {
    @Test fun validFilters() {
        for (f in listOf("a", "a/b", "a/+/c", "a/#", "#", "+", "+/+", "/a", "a/")) assertTrue(f, TopicFilter.isValid(f))
    }

    @Test fun invalidFilters() {
        for (f in listOf("", "a/#/b", "a#", "a+", "a/b+/c", "#/a", "a\u0000b", "x".repeat(257))) {
            assertFalse(f, TopicFilter.isValid(f))
        }
    }

    @Test fun exactAndSingleLevel() {
        assertTrue(TopicFilter.matches("a/b", "a/b"))
        assertFalse(TopicFilter.matches("a/b", "a/b/c"))
        assertFalse(TopicFilter.matches("a/b/c", "a/b"))
        assertTrue(TopicFilter.matches("a/+/c", "a/x/c"))
        assertFalse(TopicFilter.matches("a/+/c", "a/x/y/c"))
        assertFalse(TopicFilter.matches("a/+", "a"))
    }

    @Test fun multiLevel() {
        assertTrue(TopicFilter.matches("a/#", "a/b/c"))
        assertTrue(TopicFilter.matches("a/#", "a"))
        assertTrue(TopicFilter.matches("#", "x/y"))
        assertFalse(TopicFilter.matches("a/#", "b/c"))
    }

    @Test fun wildcardsDoNotMatchSystemTopics() {
        assertFalse(TopicFilter.matches("#", "\$SYS/x"))
        assertFalse(TopicFilter.matches("+/x", "\$SYS/x"))
        assertTrue(TopicFilter.matches("\$SYS/#", "\$SYS/x"))
    }
}
