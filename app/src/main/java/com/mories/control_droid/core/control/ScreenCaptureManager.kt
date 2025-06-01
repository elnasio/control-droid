package com.mories.control_droid.core.control

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object ScreenCaptureManager {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager

    fun setProjection(context: Context, resultCode: Int, data: Intent) {
        mediaProjectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        Log.d("ScreenCapture", "✅ MediaProjection initialized")
    }

    fun captureOnce(context: Context) {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        if (mediaProjection == null) {
            Log.e("ScreenCapture", "❌ mediaProjection is null")
            return
        }

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val surface = imageReader?.surface ?: return

        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface,
            null,
            null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                Log.w("ScreenCapture", "⚠️ No image captured")
                virtualDisplay?.release()
                imageReader?.close()
                return@postDelayed
            }

            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmapWidth = width + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            saveBitmap(context, bitmap)

            virtualDisplay?.release()
            imageReader?.close()
        }, 500)
    }
    private fun saveBitmap(context: Context, bitmap: Bitmap) {
        try {
            val file = File(context.cacheDir, "screenshot.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
                Log.d("ScreenCapture", "Screenshot saved to: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Failed to save screenshot: ${e.message}")
        }
    }
}