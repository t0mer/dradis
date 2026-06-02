package dev.tomerklein.dradis.telemetry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.tomerklein.dradis.commands.CommandSink
import dev.tomerklein.dradis.commands.LocationPayload
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "dradis.loc"

/**
 * Fetches a single fresh GPS fix via FusedLocationProviderClient and publishes
 * it to `.../location` (CLAUDE.md §9.3). Shared by the on-demand
 * [dev.tomerklein.dradis.commands.LocationHandler] and the periodic
 * [LocationReporter].
 */
object LocationPublisher {
    private val json = Json { encodeDefaults = true }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Fetch a fresh fix as a [LocationPayload] without publishing, or null if
     *  permission is missing or no fix is available. Used by telemetry. */
    suspend fun currentPayload(sink: CommandSink): LocationPayload? {
        val context = sink.appContext
        if (!hasPermission(context)) return null
        val location = currentLocation(context) ?: return null
        return LocationPayload(
            lat = location.latitude,
            lon = location.longitude,
            accuracy = location.accuracy,
            time = location.time / 1000, // epoch seconds, matching legacy
        )
    }

    /** Publish an already-fetched payload to the `location` topic. */
    fun publishPayload(sink: CommandSink, payload: LocationPayload) {
        sink.publish(sink.topics.location, json.encodeToString(payload))
    }

    suspend fun publishCurrent(sink: CommandSink): Boolean {
        if (!hasPermission(sink.appContext)) {
            sink.logInfo("Location requested but permission not granted")
            return false
        }
        val payload = currentPayload(sink) ?: run {
            sink.logInfo("Location unavailable")
            return false
        }
        sink.publish(sink.topics.location, json.encodeToString(payload))
        return true
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(10_000)
            .build()
        return runCatching { client.getCurrentLocation(request, null).await() }
            .onFailure { Log.e(TAG, "getCurrentLocation failed", it) }
            .getOrNull()
    }
}
