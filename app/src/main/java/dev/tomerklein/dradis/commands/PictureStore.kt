package dev.tomerklein.dradis.commands

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

/**
 * Saves pictures received on custom topics to app-private storage. File names are
 * generated here and never taken from a payload; the newest [MAX_FILES] are kept.
 */
class PictureStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "gallery")

    /** Returns false if the bytes are not a decodable, reasonably sized image. */
    fun save(image: ImagePayload.Image): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || w.toLong() * h > MAX_PIXELS) return false
        dir.mkdirs()
        File(dir, "${UUID.randomUUID()}.${image.extension}").writeBytes(image.bytes)
        trim()
        true
    }.getOrDefault(false)

    private fun trim() {
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_FILES)?.forEach { it.delete() }
    }

    private companion object {
        const val MAX_FILES = 100
        const val MAX_SIDE = 8000
        const val MAX_PIXELS = 40_000_000L
    }
}
