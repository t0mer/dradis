package dev.tomerklein.dradis.log

import java.time.Instant

/** Renders [LogEntry] lists for the `send_logs` / `send_html_logs` commands. */
object LogFormatter {

    /** One `<iso-time> <topic> <payload>` line per entry. */
    fun toText(entries: List<LogEntry>): String =
        entries.joinToString("\n") { "${Instant.ofEpochMilli(it.timeMillis)} ${it.topic} ${it.payload}" }

    /** A minimal HTML table; topic and payload are untrusted, so always escaped. */
    fun toHtml(entries: List<LogEntry>, title: String): String = buildString {
        append("<html><head><meta charset=\"utf-8\"><title>").append(escape(title)).append("</title></head><body>")
        append("<h1>").append(escape(title)).append("</h1><table border=\"1\">")
        entries.forEach {
            append("<tr><td>").append(Instant.ofEpochMilli(it.timeMillis))
            append("</td><td>").append(escape(it.topic))
            append("</td><td>").append(escape(it.payload))
            append("</td></tr>")
        }
        append("</table></body></html>")
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
