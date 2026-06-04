package dev.tomerklein.dradis.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dradis_settings")

/**
 * Reads/writes [DradisSettings] via Jetpack DataStore (Preferences). The whole
 * object is serialized to JSON and **encrypted at rest** with [SecureStore]
 * (AES-256-GCM, Android Keystore) so broker credentials and the CA certificate
 * are never stored in plaintext. Legacy plaintext blobs are read transparently
 * and re-encrypted on the next save (migration).
 */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("settings_json")
    // Last successfully-read Wi-Fi SSID. Kept as a separate plaintext key (not a
    // secret — it's broadcast over the air) and OUTSIDE the encrypted settings
    // blob so writing it doesn't churn the whole settings object on every read.
    private val lastSsidKey = stringPreferencesKey("last_known_ssid")

    val settings: Flow<DradisSettings> = context.dataStore.data
        .map { prefs -> prefs[key]?.let { decode(it) } ?: DradisSettings() }
        // Writing unrelated keys (e.g. last_known_ssid) re-emits DataStore; dedupe
        // so collectors don't needlessly reselect the broker / restart reporters.
        .distinctUntilChanged()

    suspend fun update(transform: (DradisSettings) -> DradisSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { decode(it) } ?: DradisSettings()
            prefs[key] = SecureStore.encrypt(json.encodeToString(transform(current)))
        }
    }

    suspend fun set(settings: DradisSettings) = update { settings }

    /** Ensure a stable per-install MQTT client id exists, generating a UUID-based
     *  one on first run. Returns the (existing or newly created) client id. */
    suspend fun ensureClientId(): String {
        var id = ""
        update { s ->
            id = s.clientId.ifBlank { "dradis-${UUID.randomUUID()}" }
            s.copy(clientId = id)
        }
        return id
    }

    /** Last Wi-Fi SSID we successfully read, or "" if none yet. Used to seed
     *  [dev.tomerklein.dradis.net.NetworkMonitor] at startup so a service restart
     *  re-picks the home (LAN) broker immediately instead of starting blind
     *  (null SSID → WAN) until the SSID resolves again. */
    suspend fun lastKnownSsid(): String =
        context.dataStore.data.map { it[lastSsidKey] ?: "" }.first()

    /** Persist the last successfully-read Wi-Fi SSID. No-op when unchanged so we
     *  don't rewrite DataStore on every (identical) network callback. */
    suspend fun setLastKnownSsid(ssid: String) {
        context.dataStore.edit { prefs ->
            if (prefs[lastSsidKey] == ssid) return@edit
            prefs[lastSsidKey] = ssid
        }
    }

    /** Re-encrypt a legacy plaintext blob in place (run once at startup). */
    suspend fun migrate() {
        context.dataStore.edit { prefs ->
            val stored = prefs[key] ?: return@edit
            val alreadyEncrypted = runCatching { SecureStore.decrypt(stored) }.isSuccess
            if (!alreadyEncrypted) {
                runCatching { json.decodeFromString<DradisSettings>(stored) }.getOrNull()?.let {
                    prefs[key] = SecureStore.encrypt(json.encodeToString(it))
                }
            }
        }
    }

    /** Decrypt-then-parse; falls back to legacy plaintext JSON for migration. */
    private fun decode(stored: String): DradisSettings? {
        val jsonStr = runCatching { SecureStore.decrypt(stored) }.getOrNull() ?: stored
        return runCatching { json.decodeFromString<DradisSettings>(jsonStr) }.getOrNull()
    }
}
