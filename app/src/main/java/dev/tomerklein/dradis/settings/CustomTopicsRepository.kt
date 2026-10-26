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

    val topics: Flow<List<CustomTopic>> =
        context.customTopicsStore.data
            // A read error must not kill the collector for the life of the service.
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { decode(it[key]) }
            .distinctUntilChanged()

    /** Validates then stores [list]. Returns the validation error, or null on success. */
    suspend fun save(list: List<CustomTopic>, builtIn: Collection<String>): String? {
        CustomTopicValidator.validateAll(list, builtIn)?.let { return it }
        val encoded = SecureStore.encrypt(json.encodeToString(list))
        context.customTopicsStore.edit { it[key] = encoded }
        return null
    }

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
            error = CustomTopicValidator.validateAll(next, builtIn)
            if (error == null) prefs[key] = SecureStore.encrypt(json.encodeToString(next))
        }
        return error
    }

    private fun decode(stored: String?): List<CustomTopic> {
        if (stored == null) return emptyList()
        val text = runCatching { SecureStore.decrypt(stored) }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<List<CustomTopic>>(text) }.getOrDefault(emptyList())
            .take(CustomTopicValidator.MAX_TOPICS)
    }
}
