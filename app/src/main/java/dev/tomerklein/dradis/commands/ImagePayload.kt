package dev.tomerklein.dradis.commands

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Turns an inbound picture payload into image bytes. Accepts raw JPEG/PNG bytes,
 * base64 text, or dradis' own `{"jpeg_b64": "..."}` photo JSON. Anything else,
 * including data that merely looks like base64, is rejected by a magic-byte check.
 */
object ImagePayload {

    class Image(val bytes: ByteArray, val extension: String)

    /** Largest image accepted, after base64 decoding. */
    const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

    // Base64 inflates by 4/3; allow the text form a little headroom.
    private const val MAX_PAYLOAD_BYTES = MAX_IMAGE_BYTES / 3 * 4 + 1024

    private val json = Json { isLenient = true }

    fun decode(payload: ByteArray): Image? {
        if (payload.isEmpty() || payload.size > MAX_PAYLOAD_BYTES) return null
        sniff(payload)?.let { return it }
        val text = String(payload, Charsets.UTF_8).trim()
        val b64 = if (text.startsWith("{")) {
            runCatching { json.parseToJsonElement(text).jsonObject["jpeg_b64"]?.jsonPrimitive?.content }.getOrNull()
        } else {
            text
        } ?: return null
        val raw = runCatching { Base64.getMimeDecoder().decode(b64) }.getOrNull() ?: return null
        return sniff(raw)
    }

    private fun sniff(b: ByteArray): Image? {
        if (b.size < 4 || b.size > MAX_IMAGE_BYTES) return null
        fun at(i: Int) = b[i].toInt() and 0xFF
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> Image(b, "jpg")
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> Image(b, "png")
            else -> null
        }
    }
}
