package dev.tomerklein.dradis.settings

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Builds and validates the **base** MQTT client id (the `-lan` / `-wan` route
 * suffix is added by the service). A broker allows only one connection per
 * client id: a second client with the same id evicts the first, which reconnects
 * and evicts the second — an endless connect/disconnect loop that often surfaces
 * as repeated `NOT_AUTHORIZED` when the broker rate-limits the storm. The id must
 * therefore be unique **per device**.
 *
 * The id is **generated once at first setup and saved locally** (see
 * [SettingsRepository.ensureClientId]). A v4 [UUID] gives 122 bits of entropy, so
 * a freshly generated id is unique across any number of devices on one broker
 * without needing a hardware identifier — note that MAC (constant
 * `02:00:00:00:00:00` since Android 6) and IMEI (`READ_PRIVILEGED_PHONE_STATE`,
 * system-only since Android 10) are **not readable by a normal app** on this
 * target, so they can't contribute.
 *
 * The one thing a saved UUID can't survive is a **data clone** (Samsung Smart
 * Switch / device transfer / a manual restore) copying it onto a second device.
 * To catch that we record [deviceFingerprint] (ANDROID_ID — read live, unique per
 * device+signing-key, never meaningfully carried onto another device) alongside
 * the id; when it no longer matches the running device, the id is regenerated.
 */
object ClientId {

    // A well-known broken ANDROID_ID shipped by some old devices/emulators; treat
    // it as unavailable so those don't all share one fingerprint.
    private const val BAD_ANDROID_ID = "9774d56d682e549c"

    /** A freshly generated, per-install client-id base. */
    fun generate(): String = "dradis-${UUID.randomUUID()}"

    /** Stable per-device fingerprint used to detect that the saved settings were
     *  copied to a DIFFERENT device, so the id can be regenerated. Read live from
     *  ANDROID_ID; null (clone-detection unavailable) when ANDROID_ID is missing
     *  or the known-bad placeholder. */
    @SuppressLint("HardwareIds")
    fun deviceFingerprint(context: Context): String? {
        val id = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        return if (id.isNullOrBlank() || id == BAD_ANDROID_ID) null else id
    }
}
