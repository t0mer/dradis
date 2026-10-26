package dev.tomerklein.dradis.mqtt

/**
 * While discovery holds an extra overlapping subscription the broker may deliver one
 * publish once per matching subscription. Identical topic+payload arriving within
 * [windowMillis] is treated as such a duplicate, so a command is never run twice.
 */
class DuplicateGuard(private val windowMillis: Long = 250, private val clock: () -> Long = System::currentTimeMillis) {
    private var lastKey: Pair<String, Int>? = null
    private var lastAt = 0L

    @Synchronized
    fun isDuplicate(topic: String, payload: ByteArray): Boolean {
        val key = topic to payload.contentHashCode()
        val now = clock()
        val dup = key == lastKey && now - lastAt < windowMillis
        lastKey = key
        lastAt = now
        return dup
    }
}
