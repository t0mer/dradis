package dev.tomerklein.dradis.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Direction of a logged MQTT message relative to this device. */
enum class Direction { IN, OUT, INFO }

data class LogEntry(
    val timeMillis: Long,
    val direction: Direction,
    val topic: String,
    val payload: String,
)

/**
 * Ring buffer of recent inbound/outbound MQTT activity, mirrored to the in-app
 * Logs screen (CLAUDE.md §13). A required feature, independent of logcat.
 */
class MqttLog(private val capacity: Int = 200) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    private fun add(entry: LogEntry) {
        _entries.update { current ->
            (current + entry).takeLast(capacity)
        }
    }

    fun inbound(topic: String, payload: String, at: Long) =
        add(LogEntry(at, Direction.IN, topic, payload))

    fun outbound(topic: String, payload: String, at: Long) =
        add(LogEntry(at, Direction.OUT, topic, payload))

    fun info(message: String, at: Long) =
        add(LogEntry(at, Direction.INFO, "", message))

    fun clear() {
        _entries.value = emptyList()
    }
}
