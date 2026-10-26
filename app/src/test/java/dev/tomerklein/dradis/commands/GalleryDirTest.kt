package dev.tomerklein.dradis.commands

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryDirTest {
    private val root: File = Files.createTempDirectory("gallery").toFile().also { it.deleteOnExit() }
    private val dir = File(root, "gallery")

    @Test fun addThenListNewestFirst() {
        val g = GalleryDir(dir)
        val a = g.add(byteArrayOf(1), "jpg")
        File(dir, a.name).setLastModified(1_000)
        val b = g.add(byteArrayOf(2, 2), "png")
        assertEquals(listOf(b.name, a.name), g.list().map { it.name })
        assertEquals(2L, g.list().first().sizeBytes)
    }

    @Test fun keepsOnlyNewestMaxFiles() {
        val g = GalleryDir(dir, maxFiles = 2)
        val names = (1..3).map { i ->
            g.add(byteArrayOf(i.toByte()), "jpg").also { File(dir, it.name).setLastModified(i * 1000L) }
        }.map { it.name }
        g.add(byteArrayOf(9), "jpg")
        val left = g.list().map { it.name }
        assertEquals(2, left.size)
        assertFalse(names[0] in left)
    }

    @Test fun deleteRemovesFile() {
        val g = GalleryDir(dir)
        val a = g.add(byteArrayOf(1), "png")
        assertTrue(g.delete(a.name))
        assertTrue(g.list().isEmpty())
        assertFalse(g.delete(a.name))
    }

    @Test fun rejectsNamesThatCouldEscapeTheDirectory() {
        val g = GalleryDir(dir)
        g.add(byteArrayOf(1), "jpg")
        File(root, "secret.jpg").writeBytes(byteArrayOf(7))
        for (bad in listOf("../secret.jpg", "..", "a/b.jpg", "secret.jpg", "", "00000000-0000-0000-0000-000000000000.gif")) {
            assertNull(bad, g.fileFor(bad))
            assertFalse(bad, g.delete(bad))
        }
        assertTrue(File(root, "secret.jpg").exists())
    }

    @Test fun ignoresForeignFilesInDirectory() {
        val g = GalleryDir(dir)
        g.add(byteArrayOf(1), "jpg")
        File(dir, "notes.txt").writeText("x")
        assertEquals(1, g.list().size)
    }

    @Test fun partialWritesAreNeverListed() {
        val g = GalleryDir(dir)
        val a = g.add(byteArrayOf(1), "jpg")
        File(dir, "${a.name}.tmp").writeBytes(byteArrayOf(1))
        assertEquals(listOf(a.name), g.list().map { it.name })
        assertEquals(null, g.fileFor("${a.name}.tmp"))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownExtension() {
        GalleryDir(dir).add(byteArrayOf(1), "html")
    }
}
