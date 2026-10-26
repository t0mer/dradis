package dev.tomerklein.dradis.settings

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** JSON export/import of the custom topic lists (inbound and outbound). Imported files are untrusted. */
object TopicsTransfer {

    /** Largest import file accepted, in bytes. */
    const val MAX_FILE_BYTES = 256 * 1024

    @Serializable
    class Export(
        val version: Int,
        val topics: List<CustomTopic> = emptyList(),
        val outbound: List<OutboundTopic> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun export(topics: List<CustomTopic>, outbound: List<OutboundTopic> = emptyList()): String =
        json.encodeToString(Export(2, topics, outbound))

    sealed interface Result {
        /** [merged] is the existing list plus the imported topics; already validated. */
        data class Ok(
            val merged: List<CustomTopic>,
            val added: Int,
            val mergedOutbound: List<OutboundTopic> = emptyList(),
            val addedOutbound: Int = 0,
        ) : Result
        data class Error(val message: String) : Result
    }

    /**
     * Parses [text] (an export object or a bare array), gives every topic a fresh id and disables it, so an
     * import can never overwrite or collide with an existing one, and validates the combined
     * list. Nothing is applied unless the whole import is valid.
     */
    fun import(
        text: String,
        existing: List<CustomTopic>,
        builtIn: Collection<String>,
        existingOutbound: List<OutboundTopic> = emptyList(),
    ): Result {
        if (text.length > MAX_FILE_BYTES) return Result.Error("File is too large")
        val (incoming, incomingOut) = try {
            when (val root = json.parseToJsonElement(text)) {
                is JsonArray -> json.decodeFromJsonElement<List<CustomTopic>>(root) to emptyList()
                is JsonObject -> json.decodeFromJsonElement<Export>(root).let { it.topics to it.outbound }
                else -> return Result.Error("Not a custom topics file")
            }
        } catch (e: Exception) {
            return Result.Error("Not a valid custom topics file")
        }
        if (incoming.isEmpty() && incomingOut.isEmpty()) return Result.Error("The file contains no topics")
        if (existing.size + incoming.size > CustomTopicValidator.MAX_TOPICS) {
            return Result.Error("At most ${CustomTopicValidator.MAX_TOPICS} custom topics")
        }
        if (existingOutbound.size + incomingOut.size > OutboundTopicValidator.MAX_TOPICS) {
            return Result.Error("At most ${OutboundTopicValidator.MAX_TOPICS} outbound topics")
        }
        // Imported topics start disabled: a shared file must not start acting (ringing, speaking,
        // subscribing, publishing) until the user has looked at it and switched it on.
        val fresh = incoming.map { it.copy(id = UUID.randomUUID().toString(), enabled = false) }
        val freshOut = incomingOut.map { it.copy(id = UUID.randomUUID().toString(), enabled = false) }
        val merged = existing + fresh
        val mergedOut = existingOutbound + freshOut
        CustomTopicValidator.validateAll(merged, builtIn + mergedOut.map { it.topic })?.let { return Result.Error(it) }
        OutboundTopicValidator.validateAll(mergedOut, builtIn, merged.map { it.filter })?.let { return Result.Error(it) }
        return Result.Ok(merged, fresh.size, mergedOut, freshOut.size)
    }
}
