package com.mories.control_droid.core.control

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import okhttp3.WebSocket
import okio.ByteString
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_DATA = "result_data"
        var activeWebSocket: WebSocket? = null
    }

    private lateinit var mediaProjection: MediaProjection
    private lateinit var imageReader: ImageReader
    private lateinit var virtualDisplay: VirtualDisplay
    private var handler: Handler? = null

    private val width = 480
    private val height = 270
    private val frameRateMs = 200L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            Log.e("ScreenCaptureService", "Result data null, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(Activity.RESULT_OK, resultData)

        startCapture()
        return START_STICKY
    }

    private fun startCapture() {
        Log.i("ScreenCaptureService", "Starting screen capture")

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "RemoteScreen",
            width,
            height,
            Resources.getSystem().displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )

        handler = Handler(Looper.getMainLooper())
        handler?.post(object : Runnable {
            @RequiresApi(Build.VERSION_CODES.R)
            override fun run() {
                try {
                    val image = imageReader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 50, stream)
                        val byteArray = stream.toByteArray()

                        activeWebSocket?.let {
                            it.send(ByteString.of(*byteArray))
                        } ?: Log.w("ScreenCaptureService", "WebSocket is null")
                    }
                } catch (e: Exception) {
                    Log.e("ScreenCaptureService", "Capture error: ${e.message}")
                } finally {
                    handler?.postDelayed(this, frameRateMs)
                }
            }
        })
    }

    override fun onDestroy() {
        Log.i("ScreenCaptureService", "Service destroyed, releasing resources")
        try {
            virtualDisplay.release()
            imageReader.close()
            mediaProjection.stop()
        } catch (e: Exception) {
            Log.w("ScreenCaptureService", "Cleanup error: ${e.message}")
        }
        handler = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}