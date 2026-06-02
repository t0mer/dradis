package dev.tomerklein.dradis.commands

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "dradis.photo"

/**
 * Headless still capture via CameraX, bound to the process lifecycle so it works
 * from the service with no Activity/preview (CLAUDE.md §9.6). Returns a
 * resized/compressed JPEG (max 1280px, quality 70 — §9.7).
 */
object PhotoCapturer {

    private const val MAX_DIM = 1280
    private const val JPEG_QUALITY = 70

    suspend fun capture(context: Context, rear: Boolean): ByteArray? {
        val provider = runCatching { awaitProvider(context) }
            .onFailure { Log.e(TAG, "camera provider unavailable", it) }
            .getOrNull() ?: return null

        val selector = if (rear) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        try {
            withContext(Dispatchers.Main) {
                provider.unbindAll()
                provider.bindToLifecycle(ProcessLifecycleOwner.get(), selector, imageCapture)
            }
            return takePicture(context, imageCapture)
        } catch (t: Throwable) {
            Log.e(TAG, "capture failed", t)
            return null
        } finally {
            withContext(Dispatchers.Main) {
                try {
                    provider.unbindAll()
                } catch (t: Throwable) {
                    Log.e(TAG, "unbindAll failed", t)
                }
            }
        }
    }

    /** Awaits the singleton [ProcessCameraProvider] without pulling in coroutines-guava. */
    private suspend fun awaitProvider(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            }, ContextCompat.getMainExecutor(context))
        }

    private suspend fun takePicture(context: Context, imageCapture: ImageCapture): ByteArray? =
        suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = runCatching { compress(image) }
                            .onFailure { Log.e(TAG, "encode failed", it) }
                            .getOrNull()
                        image.close()
                        if (cont.isActive) cont.resume(bytes)
                    }

                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        Log.e(TAG, "takePicture error", exception)
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }

    private fun compress(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val raw = ByteArray(buffer.remaining()).also { buffer.get(it) }
        var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)

        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        bmp = scaleToMax(bmp, MAX_DIM)

        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun scaleToMax(bmp: Bitmap, max: Int): Bitmap {
        val largest = maxOf(bmp.width, bmp.height)
        if (largest <= max) return bmp
        val ratio = max.toFloat() / largest
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }
}
