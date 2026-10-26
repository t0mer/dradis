package dev.tomerklein.dradis.mqtt

/**
 * While discovery holds an extra overlapping subscription the broker may deliver one
 * publish once per matching subscription. Identical topic+payload arriving within
 * [windowMillis] is treated as such a duplicate, so a command is never run twice.
 * Every message inside the window is remembered, so interleaved duplicates (A, B, A) are
 * caught; the cost is that two genuinely identical commands within the window collapse.
 */
class DuplicateGuard(private val windowMillis: Long = 250, private val clock: () -> Long = System::currentTimeMillis) {
    private val recent = LinkedHashMap<Pair<String, Int>, Long>()

    @Synchronized
    fun isDuplicate(topic: String, payload: ByteArray): Boolean {
        val now = clock()
        recent.values.removeAll { now - it >= windowMillis }
        val key = topic to payload.contentHashCode()
        val dup = key in recent
        recent[key] = now
        return dup
    }
}
