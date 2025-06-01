package com.mories.control_droid.core.control

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ScreenCaptureManager {
    private var mediaProjection: MediaProjection? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var autoCaptureJob: Job? = null

    fun setProjection(context: Context, resultCode: Int, data: Intent) {
        mediaProjectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        Log.d("ScreenCapture", "✅ MediaProjection initialized")
    }

    fun isReady(): Boolean {
        return ::mediaProjectionManager.isInitialized && mediaProjection != null
    }

    fun startAutoCapture(context: Context) {
        if (autoCaptureJob?.isActive == true || !isReady()) return

        autoCaptureJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val bitmap = captureOnceSuspend(context)
                if (bitmap != null) {
                    saveBitmap(context, bitmap)
                }
                delay(2000)
            }
        }
    }

    suspend fun captureOnceSuspend(context: Context): Bitmap? = withContext(Dispatchers.Main) {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        if (!isReady()) {
            Log.e("ScreenCapture", "❌ MediaProjection not initialized")
            return@withContext null
        }

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val surface = imageReader.surface

        val virtualDisplay: VirtualDisplay? = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface,
            null,
            null
        )

        delay(500)

        val image = imageReader.acquireLatestImage()
        if (image == null) {
            Log.w("ScreenCapture", "⚠️ No image captured")
            virtualDisplay?.release()
            imageReader.close()
            return@withContext null
        }

        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmapWidth = width + rowPadding / pixelStride
        val tempBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        tempBitmap.copyPixelsFromBuffer(buffer)
        image.close()

        val croppedBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, height)
        tempBitmap.recycle()

        virtualDisplay?.release()
        imageReader.close()

        return@withContext croppedBitmap
    }

    internal fun saveBitmap(context: Context, bitmap: Bitmap) {
        try {
            val file = File(context.cacheDir, "screenshot.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
                Log.d("ScreenCapture", "📸 Screenshot saved: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("ScreenCapture", "❌ Failed to save: ${e.message}")
        }
    }
}