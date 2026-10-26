package dev.tomerklein.dradis.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import dev.tomerklein.dradis.mqtt.Topics
import org.junit.Test

class CustomTopicValidatorTest {
    private val builtIn = listOf("dradis/phone/ping", "dradis/phone/cmd/+")
    private fun t(id: String = "1", filter: String = "home/door", name: String = "Door", f: CustomTopic.() -> CustomTopic = { this }) =
        CustomTopic(id = id, name = name, filter = filter).f()

    @Test fun validTopicPasses() = assertNull(CustomTopicValidator.validate(t(), emptyList(), builtIn))

    @Test fun rejectsBadFilterAndName() {
        assertNotNull(CustomTopicValidator.validate(t(filter = "a/#/b"), emptyList(), builtIn))
        assertNotNull(CustomTopicValidator.validate(t(name = " "), emptyList(), builtIn))
    }

    @Test fun rejectsBuiltInFilter() =
        assertNotNull(CustomTopicValidator.validate(t(filter = "dradis/phone/ping"), emptyList(), builtIn))

    @Test fun rejectsFiltersOverlappingBuiltInOrOutboundTopics() {
        val own = builtIn + Topics("dradis", "phone").outboundTopics()
        for (f in listOf("#", "+/+/+", "dradis/phone/#", "dradis/+/ping", "dradis/phone/cmd/restart", "dradis/phone/battery")) {
            assertNotNull(f, CustomTopicValidator.validate(t(filter = f), emptyList(), own))
        }
        assertNull(CustomTopicValidator.validate(t(filter = "home/#"), emptyList(), own))
    }

    @Test fun rejectsOverlappingCustomTopics() {
        val a = t(id = "1", filter = "home/#")
        assertNotNull(CustomTopicValidator.validate(t(id = "2", filter = "home/door"), listOf(a), builtIn))
        assertNull(CustomTopicValidator.validate(t(id = "2", filter = "office/door"), listOf(a), builtIn))
    }

    @Test fun rejectsBadIds() {
        for (id in listOf("", "a b", "a/b", "x".repeat(65))) {
            assertNotNull(id, CustomTopicValidator.validate(t(id = id), emptyList(), builtIn))
        }
    }

    @Test fun rejectsDuplicateFilterButNotSelf() {
        val a = t(id = "1")
        assertNotNull(CustomTopicValidator.validate(t(id = "2"), listOf(a), builtIn))
        assertNull(CustomTopicValidator.validate(a, listOf(a), builtIn))
    }

    @Test fun extractedNeedsValidExtractor() {
        assertNotNull(CustomTopicValidator.validate(t { copy(type = CustomTopicType.EXTRACTED) }, emptyList(), builtIn))
        assertNotNull(CustomTopicValidator.validate(t { copy(type = CustomTopicType.EXTRACTED, extractor = "(") }, emptyList(), builtIn))
        assertNull(CustomTopicValidator.validate(t { copy(type = CustomTopicType.EXTRACTED, extractor = "$.a.b") }, emptyList(), builtIn))
        assertNull(CustomTopicValidator.validate(t { copy(type = CustomTopicType.EXTRACTED, extractor = "(\\d+)") }, emptyList(), builtIn))
    }

    @Test fun keyValueNeedsSeparator() =
        assertNotNull(CustomTopicValidator.validate(t { copy(type = CustomTopicType.KEY_VALUE, separator = "") }, emptyList(), builtIn))

    @Test fun validateAllChecksCountAndIds() {
        val many = (1..101).map { t(id = "$it", filter = "t/$it") }
        assertEquals("At most 100 custom topics", CustomTopicValidator.validateAll(many, builtIn))
        assertEquals("Duplicate ids", CustomTopicValidator.validateAll(listOf(t(id = "1"), t(id = "1", filter = "x")), builtIn))
        assertNull(CustomTopicValidator.validateAll(listOf(t(id = "1"), t(id = "2", filter = "x")), builtIn))
    }
}
