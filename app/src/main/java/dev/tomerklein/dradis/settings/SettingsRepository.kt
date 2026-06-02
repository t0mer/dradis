package dev.tomerklein.dradis.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dradis_settings")

/**
 * Reads/writes [DradisSettings] via Jetpack DataStore (Preferences), storing the
 * whole object as one JSON blob. Credentials live here at runtime — never in
 * code or git (CLAUDE.md §14).
 */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("settings_json")

    val settings: Flow<DradisSettings> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<DradisSettings>(it) }.getOrNull() }
            ?: DradisSettings()
    }

    suspend fun update(transform: (DradisSettings) -> DradisSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<DradisSettings>(it) }.getOrNull()
            } ?: DradisSettings()
            prefs[key] = json.encodeToString(transform(current))
        }
    }

    suspend fun set(settings: DradisSettings) = update { settings }
}
