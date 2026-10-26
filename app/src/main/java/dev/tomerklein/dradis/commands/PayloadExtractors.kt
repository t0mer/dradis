package dev.tomerklein.dradis.commands

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Value extraction for custom topics. Payloads are untrusted, so input is capped
 * and every failure yields null instead of throwing.
 */
object PayloadExtractors {

    /** Longest payload prefix examined; bounds regex and JSON work. */
    const val MAX_INPUT = 8 * 1024

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

    private fun regex(input: String, pattern: String): String? = runCatching {
        Regex(pattern).find(input)?.let { m -> if (m.groupValues.size > 1) m.groupValues[1] else m.value }
    }.getOrNull()

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
