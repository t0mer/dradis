package dev.tomerklein.dradis.settings

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** JSON export/import of the custom topic list. Imported files are untrusted. */
object TopicsTransfer {

    /** Largest import file accepted, in bytes. */
    const val MAX_FILE_BYTES = 256 * 1024

    @Serializable
    class Export(val version: Int, val topics: List<CustomTopic>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun export(topics: List<CustomTopic>): String = json.encodeToString(Export(1, topics))

    sealed interface Result {
        /** [merged] is the existing list plus the imported topics; already validated. */
        data class Ok(val merged: List<CustomTopic>, val added: Int) : Result
        data class Error(val message: String) : Result
    }

    /**
     * Parses [text] (an export object or a bare array), gives every topic a fresh id so an
     * import can never overwrite or collide with an existing one, and validates the combined
     * list. Nothing is applied unless the whole import is valid.
     */
    fun import(text: String, existing: List<CustomTopic>, builtIn: Collection<String>): Result {
        if (text.length > MAX_FILE_BYTES) return Result.Error("File is too large")
        val incoming = try {
            when (val root = json.parseToJsonElement(text)) {
                is JsonArray -> json.decodeFromJsonElement<List<CustomTopic>>(root)
                is JsonObject -> json.decodeFromJsonElement<Export>(root).topics
                else -> return Result.Error("Not a custom topics file")
            }
        } catch (e: Exception) {
            return Result.Error("Not a valid custom topics file")
        }
        if (incoming.isEmpty()) return Result.Error("The file contains no topics")
        if (existing.size + incoming.size > CustomTopicValidator.MAX_TOPICS) {
            return Result.Error("At most ${CustomTopicValidator.MAX_TOPICS} custom topics")
        }
        val fresh = incoming.map { it.copy(id = UUID.randomUUID().toString()) }
        val merged = existing + fresh
        CustomTopicValidator.validateAll(merged, builtIn)?.let { return Result.Error(it) }
        return Result.Ok(merged, fresh.size)
    }
}
