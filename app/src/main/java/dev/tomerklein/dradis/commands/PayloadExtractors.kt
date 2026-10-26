package dev.tomerklein.dradis.commands

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Value extraction for custom topics. Payloads are untrusted, so input is capped
 * and every failure yields null instead of throwing.
 */
object PayloadExtractors {

    /** Longest payload prefix examined; bounds regex and JSON work. */
    const val MAX_INPUT = 8 * 1024

    private const val MAX_CACHED_PATTERNS = 64

    private val json = Json { isLenient = true }

    /** `$.a.b[0]` / `$['k']` JSONPath subset when [extractor] starts with `$`,
     *  otherwise a regex (capture group 1 if present, else the whole match). */
    fun extract(payload: String, extractor: String): String? {
        val input = payload.take(MAX_INPUT)
        return if (extractor.startsWith("$")) jsonPath(input, extractor) else regex(input, extractor)
    }

    /** Splits [payload] at the first [separator]; without one the whole payload is
     *  the value and [defaultLabel] the label. */
    fun keyValue(payload: String, separator: String, defaultLabel: String): Pair<String, String> {
        val text = payload.take(MAX_INPUT)
        val i = if (separator.isEmpty()) -1 else text.indexOf(separator)
        if (i < 0) return defaultLabel to text.trim()
        val label = text.substring(0, i).trim().ifEmpty { defaultLabel }
        return label to text.substring(i + separator.length).trim()
    }

    /** Time budget for one regex match; patterns are user-defined and java.util.regex
     *  has no timeout of its own, so a catastrophic pattern would pin a thread. */
    const val REGEX_BUDGET_MS = 100L

    private val regexCache = ConcurrentHashMap<String, Regex>()

    private class RegexTimeout : RuntimeException()

    /** Throws [RegexTimeout] from `charAt` once the deadline has passed, which
     *  aborts the matcher wherever it is backtracking. */
    private class DeadlineChars(private val s: CharSequence, private val deadlineNanos: Long) : CharSequence {
        override val length: Int get() = s.length
        override fun get(index: Int): Char {
            if (System.nanoTime() - deadlineNanos > 0) throw RegexTimeout()
            return s[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            DeadlineChars(s.subSequence(startIndex, endIndex), deadlineNanos)
        override fun toString(): String = s.toString()
    }

    private fun compiled(pattern: String): Regex {
        if (regexCache.size >= MAX_CACHED_PATTERNS) regexCache.clear()
        return regexCache.getOrPut(pattern) { Regex(pattern) }
    }

    /** Group 1 if the pattern has one (null when it did not capture anything),
     *  else the whole match. Null on no match, a bad pattern or a timeout. */
    private fun regex(input: String, pattern: String): String? = try {
        val deadline = System.nanoTime() + REGEX_BUDGET_MS * 1_000_000
        compiled(pattern).find(DeadlineChars(input, deadline))?.let { m ->
            if (m.groups.size > 1) m.groups[1]?.value?.takeIf { it.isNotEmpty() } else m.value
        }
    } catch (_: RegexTimeout) {
        null
    } catch (_: RuntimeException) {
        null
    } catch (_: StackOverflowError) {
        // java.util.regex recurses per iteration on patterns like (a|b)*; an imported or
        // hostile extractor must not be able to take the service down.
        null
    }

    private fun jsonPath(input: String, path: String): String? = runCatching {
        var node: JsonElement = json.parseToJsonElement(input)
        for (seg in segments(path) ?: return null) {
            node = when (seg) {
                is Seg.Key -> (node as? JsonObject)?.get(seg.name) ?: return null
                is Seg.Index -> (node as? JsonArray)?.getOrNull(seg.i) ?: return null
            }
        }
        when (node) {
            is JsonNull -> null
            is JsonPrimitive -> node.content
            else -> node.toString()
        }
    }.getOrNull()

    private sealed interface Seg {
        data class Key(val name: String) : Seg
        data class Index(val i: Int) : Seg
    }

    private fun segments(path: String): List<Seg>? {
        val out = mutableListOf<Seg>()
        var p = 1 // skip the leading '$'
        while (p < path.length) {
            when (path[p]) {
                '.' -> {
                    val end = (p + 1 until path.length).firstOrNull { path[it] == '.' || path[it] == '[' } ?: path.length
                    if (end == p + 1) return null
                    out += Seg.Key(path.substring(p + 1, end))
                    p = end
                }
                '[' -> {
                    val close = path.indexOf(']', p)
                    if (close < 0) return null
                    val inner = path.substring(p + 1, close)
                    val quoted = inner.length >= 2 && inner.first() in "'\"" && inner.last() == inner.first()
                    out += when {
                        quoted -> Seg.Key(inner.substring(1, inner.length - 1))
                        else -> Seg.Index(inner.toIntOrNull()?.takeIf { it >= 0 } ?: return null)
                    }
                    p = close + 1
                }
                else -> return null
            }
        }
        return out
    }
}
