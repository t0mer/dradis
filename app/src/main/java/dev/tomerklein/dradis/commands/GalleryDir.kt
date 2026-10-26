package dev.tomerklein.dradis.commands

import java.io.File
import java.util.UUID

/** One stored picture. [name] is the file name inside the gallery directory. */
data class GalleryItem(val name: String, val modifiedMillis: Long, val sizeBytes: Long)

/**
 * File-level gallery storage (Context-free so it can be unit tested). Names are
 * generated here; lookups accept only names of that exact shape, so a caller can
 * never reach outside [dir].
 */
class GalleryDir(private val dir: File, private val maxFiles: Int = MAX_FILES) {

    /** Writes [bytes] under a fresh random name, then drops everything beyond the newest [maxFiles]. */
    fun add(bytes: ByteArray, extension: String): GalleryItem {
        require(extension == "jpg" || extension == "png") { "bad extension" }
        dir.mkdirs()
        val file = File(dir, "${UUID.randomUUID()}.$extension")
        file.writeBytes(bytes)
        trim()
        return GalleryItem(file.name, file.lastModified(), file.length())
    }

    /** Stored pictures, newest first. */
    fun list(): List<GalleryItem> =
        dir.listFiles().orEmpty()
            .filter { it.isFile && isValidName(it.name) }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .map { GalleryItem(it.name, it.lastModified(), it.length()) }

    /** The file for [name], or null if the name is malformed or the file is gone. */
    fun fileFor(name: String): File? =
        if (isValidName(name)) File(dir, name).takeIf { it.isFile } else null

    fun delete(name: String): Boolean = fileFor(name)?.delete() ?: false

    private fun trim() {
        list().drop(maxFiles).forEach { delete(it.name) }
    }

    companion object {
        const val MAX_FILES = 100
        private val NAME = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png)""")
        fun isValidName(name: String) = NAME.matches(name)
    }
}
