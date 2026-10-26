package dev.tomerklein.dradis.mqtt

/** MQTT topic-filter syntax check and matching (MQTT 3.1.1 §4.7). */
object TopicFilter {

    const val MAX_LENGTH = 256

    fun isValid(filter: String): Boolean {
        if (filter.isEmpty() || filter.length > MAX_LENGTH || '\u0000' in filter) return false
        val levels = filter.split('/')
        return levels.withIndex().all { (i, level) ->
            when {
                level == "#" -> i == levels.lastIndex
                level == "+" -> true
                else -> '#' !in level && '+' !in level
            }
        }
    }

    /** True if [topic] (a concrete topic name) matches [filter]. */
    fun matches(filter: String, topic: String): Boolean {
        val f = filter.split('/')
        val t = topic.split('/')
        // Wildcards at the first level never match `$`-prefixed system topics.
        if (topic.startsWith("$") && (f[0] == "+" || f[0] == "#")) return false
        for (i in f.indices) {
            when (f[i]) {
                "#" -> return true
                "+" -> if (i >= t.size) return false
                else -> if (i >= t.size || f[i] != t[i]) return false
            }
        }
        return f.size == t.size
    }
}
