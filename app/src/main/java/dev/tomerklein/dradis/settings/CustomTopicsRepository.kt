package dev.tomerklein.dradis.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.customTopicsStore by preferencesDataStore(name = "dradis_custom_topics")

/**
 * Persists the user's custom topics in their own DataStore file, as one
 * AES-GCM-encrypted JSON string, so [DradisSettings] and its form are untouched.
 */
class CustomTopicsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("topics_json")
    private val outKey = stringPreferencesKey("outbound_json")

    val topics: Flow<List<CustomTopic>> =
        context.customTopicsStore.data
            // A read error must not kill the collector for the life of the service.
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { decode(it[key]) }
            .distinctUntilChanged()

    /** Topics the phone publishes device values to. */
    val outbound: Flow<List<OutboundTopic>> =
        context.customTopicsStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { decodeOutbound(it[outKey]) }
            .distinctUntilChanged()

    /** Validates then stores [list]. Returns the validation error, or null on success. */
    suspend fun save(list: List<CustomTopic>, builtIn: Collection<String>): String? =
        update(builtIn) { list }

    /**
     * Atomic read-modify-write: [transform] sees the stored list inside DataStore's serialized
     * edit, so quick successive changes cannot overwrite each other. Returns the validation
     * error (nothing is written then), or null on success.
     */
    suspend fun update(
        builtIn: Collection<String>,
        transform: (List<CustomTopic>) -> List<CustomTopic>,
    ): String? {
        var error: String? = null
        context.customTopicsStore.edit { prefs ->
            val next = transform(decode(prefs[key]))
            // A custom inbound filter must not cover a topic the phone publishes on.
            val reserved = builtIn + decodeOutbound(prefs[outKey]).map { it.topic }
            error = CustomTopicValidator.validateAll(next, reserved)
            if (error == null) prefs[key] = SecureStore.encrypt(json.encodeToString(next))
        }
        return error
    }

    /** Atomic read-modify-write of the outbound list, validated against the app's own topics
     *  ([builtIn]) and the stored inbound custom filters. Returns the error, or null. */
    suspend fun updateOutbound(
        builtIn: Collection<String>,
        reservedPrefix: String,
        transform: (List<OutboundTopic>) -> List<OutboundTopic>,
    ): String? {
        var error: String? = null
        context.customTopicsStore.edit { prefs ->
            val next = transform(decodeOutbound(prefs[outKey]))
            val filters = decode(prefs[key]).map { it.filter }
            error = OutboundTopicValidator.validateAll(next, builtIn, filters, reservedPrefix)
            if (error == null) prefs[outKey] = SecureStore.encrypt(json.encodeToString(next))
        }
        return error
    }

    /** Both lists changed together (import): validated as a pair, written atomically. */
    suspend fun updateBoth(
        builtIn: Collection<String>,
        reservedPrefix: String,
        transform: (List<CustomTopic>, List<OutboundTopic>) -> Pair<List<CustomTopic>, List<OutboundTopic>>,
    ): String? {
        var error: String? = null
        context.customTopicsStore.edit { prefs ->
            val (c, o) = transform(decode(prefs[key]), decodeOutbound(prefs[outKey]))
            error = CustomTopicValidator.validateAll(c, builtIn + o.map { it.topic })
                ?: OutboundTopicValidator.validateAll(o, builtIn, c.map { it.filter }, reservedPrefix)
            if (error == null) {
                prefs[key] = SecureStore.encrypt(json.encodeToString(c))
                prefs[outKey] = SecureStore.encrypt(json.encodeToString(o))
            }
        }
        return error
    }

    private fun decodeOutbound(stored: String?): List<OutboundTopic> {
        if (stored == null) return emptyList()
        val text = runCatching { SecureStore.decrypt(stored) }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<List<OutboundTopic>>(text) }.getOrDefault(emptyList())
            .take(OutboundTopicValidator.MAX_TOPICS)
    }

    private fun decode(stored: String?): List<CustomTopic> {
        if (stored == null) return emptyList()
        val text = runCatching { SecureStore.decrypt(stored) }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<List<CustomTopic>>(text) }.getOrDefault(emptyList())
            .take(CustomTopicValidator.MAX_TOPICS)
    }
}
