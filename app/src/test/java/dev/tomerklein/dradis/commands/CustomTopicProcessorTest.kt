package dev.tomerklein.dradis.commands

import dev.tomerklein.dradis.settings.CustomTopic
import dev.tomerklein.dradis.settings.CustomTopicType
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.settings.NotifyIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEffects(var accept: Boolean = true) : CustomTopicEffects {
    val notified = mutableListOf<Triple<String, String, NotifyIcon>>()
    val keys = mutableListOf<String>()
    val spoken = mutableListOf<String>()
    val alarms = mutableListOf<Int>()
    val pictures = mutableListOf<ImagePayload.Image>()
    override fun notify(key: String, title: String, text: String, icon: NotifyIcon) { keys += key; notified += Triple(title, text, icon) }
    override fun speak(text: String) { spoken += text }
    override fun alarm(seconds: Int) { alarms += seconds }
    override fun savePicture(image: ImagePayload.Image): Boolean { pictures += image; return accept }
}

class CustomTopicProcessorTest {
    private val fx = FakeEffects()
    private var now = 42L
    private val p = CustomTopicProcessor(fx, clock = { now })
    private val logs = mutableListOf<String>()
    private val settings = DradisSettings()
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1)

    private fun topic(type: CustomTopicType, f: CustomTopic.() -> CustomTopic = { this }) =
        CustomTopic(id = "id", name = "Door", filter = "home/door", type = type).f()

    /** Each call is past the per-topic cooldown of the previous one. */
    private fun run(t: CustomTopic, payload: String, s: DradisSettings = settings) {
        p.process(t, payload.toByteArray(), s) { logs += it }
        now += CustomTopicProcessor.COOLDOWN_MS
    }

    @Test fun textRecordsValueAndNotifiesOnlyWhenEnabled() {
        run(topic(CustomTopicType.TEXT), " open ")
        assertEquals(ReceivedValue(null, "open", 42L), p.values.value["id"])
        assertTrue(fx.notified.isEmpty() && fx.spoken.isEmpty())

        run(topic(CustomTopicType.TEXT) { copy(notify = true, speak = true, icon = NotifyIcon.INFO) }, "open")
        assertEquals(listOf(Triple("Door", "open", NotifyIcon.INFO)), fx.notified)
        assertEquals(listOf("open"), fx.spoken)
    }

    @Test fun globalSwitchesStillApply() {
        val off = settings.copy(notifyEnabled = false, ttsEnabled = false)
        run(topic(CustomTopicType.TEXT) { copy(notify = true, speak = true) }, "x", off)
        assertTrue(fx.notified.isEmpty() && fx.spoken.isEmpty())
        assertEquals("x", p.values.value["id"]?.value)
    }

    @Test fun keyValueUsesLabelAsTitle() {
        run(topic(CustomTopicType.KEY_VALUE) { copy(notify = true) }, "temp: 21")
        assertEquals(ReceivedValue("temp", "21", 42L), p.values.value["id"])
        // The title is the topic's own name, never payload-controlled text.
        assertEquals("Door" to "temp: 21", fx.notified.single().let { it.first to it.second })
    }

    @Test fun extractedMatchOrNoValue() {
        run(topic(CustomTopicType.EXTRACTED) { copy(extractor = "$.t") }, """{"t":5}""")
        assertEquals("5", p.values.value["id"]?.value)
        run(topic(CustomTopicType.EXTRACTED) { copy(id = "other", extractor = "$.zz") }, """{"t":5}""")
        assertNull(p.values.value["other"])
        assertTrue(logs.any { "no value" in it })
    }

    @Test fun alarmUsesConfiguredDurationAndGate() {
        run(topic(CustomTopicType.ALARM), "x", settings.copy(alarmDurationSeconds = 7))
        assertEquals(listOf(7), fx.alarms)
        run(topic(CustomTopicType.ALARM), "x", settings.copy(pingEnabled = false))
        assertEquals(listOf(7), fx.alarms)
    }

    @Test fun pictureSavedOrRejected() {
        p.process(topic(CustomTopicType.PICTURE), jpeg, settings) { logs += it }
        assertEquals(1, fx.pictures.size)
        assertEquals("Picture received", p.values.value["id"]?.value)

        p.process(topic(CustomTopicType.PICTURE) { copy(id = "b") }, "not an image".toByteArray(), settings) { logs += it }
        assertEquals(1, fx.pictures.size)
        fx.accept = false
        p.process(topic(CustomTopicType.PICTURE) { copy(id = "c") }, jpeg, settings) { logs += it }
        assertNull(p.values.value["c"])
        assertEquals(2, logs.count { "picture rejected" in it })
    }

    @Test fun deliveredTextIsLengthLimited() {
        run(topic(CustomTopicType.TEXT) { copy(notify = true) }, "y".repeat(5000))
        assertEquals(200, fx.notified.single().second.length)
        assertEquals(500, p.values.value["id"]?.value?.length)
    }

    @Test fun retainDropsRemovedTopics() {
        run(topic(CustomTopicType.TEXT), "a")
        p.retain(emptySet())
        assertTrue(p.values.value.isEmpty())
    }

    @Test fun blankPayloadIsIgnoredForEveryType() {
        for (type in CustomTopicType.values()) {
            run(topic(type) { copy(id = type.name, notify = true, extractor = "$.a") }, "   ")
        }
        p.process(topic(CustomTopicType.ALARM), ByteArray(0), settings) { logs += it }
        assertTrue(fx.alarms.isEmpty() && fx.notified.isEmpty())
        assertTrue(p.values.value.isEmpty())
        assertTrue(logs.all { "empty payload" in it })
    }

    @Test fun cooldownDropsRapidRepeatsPerTopic() {
        val t = topic(CustomTopicType.TEXT) { copy(notify = true) }
        p.process(t, "a".toByteArray(), settings) { logs += it }
        now += CustomTopicProcessor.COOLDOWN_MS - 1
        p.process(t, "b".toByteArray(), settings) { logs += it }
        assertEquals(listOf("a"), fx.notified.map { it.second })
        p.process(t.copy(id = "other"), "c".toByteArray(), settings) { logs += it }
        assertEquals(listOf("a", "c"), fx.notified.map { it.second })
        now += 1
        p.process(t, "d".toByteArray(), settings) { logs += it }
        assertEquals(listOf("a", "c", "d"), fx.notified.map { it.second })
    }

    @Test fun notificationsUseAStableKeyPerTopic() {
        val t = topic(CustomTopicType.TEXT) { copy(notify = true) }
        run(t, "a")
        run(t, "b")
        assertEquals(listOf("id", "id"), fx.keys)
    }

    @Test fun zeroAlarmDurationIsIgnored() {
        run(topic(CustomTopicType.ALARM), "x", settings.copy(alarmDurationSeconds = 0))
        assertTrue(fx.alarms.isEmpty())
    }
}
