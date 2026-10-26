package dev.tomerklein.dradis.commands

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class ImagePayloadTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    @Test fun rawJpegAndPng() {
        assertEquals("jpg", ImagePayload.decode(jpeg)?.extension)
        assertEquals("png", ImagePayload.decode(png)?.extension)
        assertArrayEquals(jpeg, ImagePayload.decode(jpeg)?.bytes)
    }

    @Test fun base64Text() {
        assertArrayEquals(jpeg, ImagePayload.decode(b64(jpeg).toByteArray())?.bytes)
        assertNotNull(ImagePayload.decode("  ${b64(png)}\n".toByteArray()))
    }

    @Test fun dradisPhotoJson() {
        val json = """{"camera":"rear","time":1,"jpeg_b64":"${b64(jpeg)}"}"""
        assertArrayEquals(jpeg, ImagePayload.decode(json.toByteArray())?.bytes)
    }

    @Test fun rejectsNonImages() {
        assertNull(ImagePayload.decode(ByteArray(0)))
        assertNull(ImagePayload.decode("hello world".toByteArray()))
        assertNull(ImagePayload.decode(b64("not an image".toByteArray()).toByteArray()))
        assertNull(ImagePayload.decode("""{"jpeg_b64":"!!!"}""".toByteArray()))
        assertNull(ImagePayload.decode("""{"other":1}""".toByteArray()))
    }

    @Test fun rejectsOversize() {
        val big = jpeg + ByteArray(ImagePayload.MAX_IMAGE_BYTES)
        assertNull(ImagePayload.decode(big))
        assertNull(ImagePayload.decode(ByteArray(ImagePayload.MAX_IMAGE_BYTES * 2)))
    }
}
