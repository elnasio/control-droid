package com.mories.control_droid.core.control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object ScreenCaptureManager {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    fun captureOnce(context: Context) {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            val image = imageReader?.acquireLatestImage()
            image?.let {
                val planes = it.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                it.close()

                saveBitmap(context, bitmap)
            }

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