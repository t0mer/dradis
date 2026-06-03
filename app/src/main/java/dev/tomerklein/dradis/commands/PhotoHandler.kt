package dev.tomerklein.dradis.commands

import android.Manifest
import android.content.pm.PackageManager
import android.util.Base64
import androidx.core.content.ContextCompat
import dev.tomerklein.dradis.mqtt.Topics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * On `takephoto`, captures a still from the requested camera and publishes a
 * base64 JPEG to `.../photo` (CLAUDE.md §9.6, §9.7). Payload: `{"camera":"front"|"rear"}`.
 */
class PhotoHandler : CommandHandler {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun handles(topic: String, topics: Topics): Boolean =
        topic == topics.takePhoto

    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        if (!sink.settings.cameraEnabled) {
            sink.logInfo("Camera disabled in settings; ignoring")
            return
        }
        // Ignore empty payloads (e.g. a retained-clear from MQTT Explorer's
        // "delete topic") so they don't trigger a spurious capture. Send `{}`
        // to use the default camera.
        if (payload.isBlank()) {
            sink.logInfo("Photo ignored: empty payload (retained-clear)")
            return
        }
        if (ContextCompat.checkSelfPermission(sink.appContext, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sink.logInfo("Photo requested but CAMERA permission not granted")
            return
        }

        val cmd = payload.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<PhotoCommand>(it) }.getOrNull() }
            ?: PhotoCommand()
        val camera = cmd.camera.takeIf { it.isNotBlank() }
        val rear = if (camera == null) {
            sink.settings.cameraDefaultRear
        } else {
            !camera.equals("front", ignoreCase = true)
        }

        sink.logInfo("Capturing ${if (rear) "rear" else "front"} photo…")
        val jpeg = PhotoCapturer.capture(sink.appContext, rear)
        if (jpeg == null) {
            sink.logInfo("Photo capture failed")
            return
        }

        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val out = PhotoPayload(
            camera = if (rear) "rear" else "front",
            time = System.currentTimeMillis() / 1000,
            jpegB64 = b64,
        )
        sink.publish(sink.topics.photo, json.encodeToString(out))
        sink.logInfo("Photo published (${jpeg.size / 1024} KB JPEG)")
    }
}
