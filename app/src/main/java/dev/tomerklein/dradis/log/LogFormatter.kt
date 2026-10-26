package dev.tomerklein.dradis.log

import java.time.Instant

/**
 * Renders [LogEntry] lists for the `send_logs` / `send_html_logs` commands.
 * Dumps leave the device, so every entry is redacted, capped and flattened to
 * one line first (topics and payloads are untrusted).
 */
object LogFormatter {

    /** Longest topic or payload kept per dumped entry; bounds the dump size. */
    const val MAX_FIELD_CHARS = 300

    /** Mask phone-number-like digit runs (keep the last 3 for context). */
    fun redactPii(s: String): String =
        Regex("""\d{7,}""").replace(s) { "•••" + it.value.takeLast(3) }

    /** One `<iso-time> <topic> <payload>` line per entry. */
    fun toText(entries: List<LogEntry>): String =
        entries.map(::sanitize).joinToString("\n") {
            "${Instant.ofEpochMilli(it.timeMillis)} ${it.topic} ${it.payload}"
        }

    /** A minimal HTML table; topic and payload are untrusted, so always escaped. */
    fun toHtml(entries: List<LogEntry>, title: String): String = buildString {
        append("<html><head><meta charset=\"utf-8\"><title>").append(escape(title)).append("</title></head><body>")
        append("<h1>").append(escape(title)).append("</h1><table border=\"1\">")
        entries.map(::sanitize).forEach {
            append("<tr><td>").append(Instant.ofEpochMilli(it.timeMillis))
            append("</td><td>").append(escape(it.topic))
            append("</td><td>").append(escape(it.payload))
            append("</td></tr>")
        }
        append("</table></body></html>")
    }

    private fun sanitize(e: LogEntry): LogEntry =
        e.copy(topic = clean(e.topic), payload = clean(e.payload))

    // Redact before capping so a digit run cut by the cap is still masked.
    private fun clean(s: String): String {
        val flat = redactPii(s).replace(Regex("[\\r\\n]+"), " ")
        return if (flat.length > MAX_FIELD_CHARS) flat.take(MAX_FIELD_CHARS) + "…" else flat
    }

    internal fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
