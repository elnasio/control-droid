package com.mories.control_droid.core.server

import android.content.Context
import android.util.Log
import com.mories.control_droid.core.control.AccessibilityController
import com.mories.control_droid.core.control.ScreenCaptureManager
import com.mories.control_droid.core.model.DeviceAction
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton HTTP server yang berjalan di device TARGET.
 * Berfungsi untuk merespons scan dari device CONTROLLER melalui endpoint /ping.
 */
object TargetHttpServer : NanoHTTPD(8080) {

    private val isRunning = AtomicBoolean(false)
    private var getContext: (() -> Context)? = null

    fun startServer(getContextProvider: () -> Context) {
        if (isRunning.get()) return

        getContext = getContextProvider
        try {
            start(SOCKET_READ_TIMEOUT, false)
            isRunning.set(true)
            Log.d("TargetHttpServer", "✅ Started on port 8080")
        } catch (e: Exception) {
            Log.e("TargetHttpServer", "❌ Failed to start: ${e.message}")
        }
    }

    fun stopServer() {
        if (!isRunning.get()) return
        stop()
        isRunning.set(false)
        Log.d("TargetHttpServer", "🛑 Stopped.")
    }

    override fun serve(session: IHTTPSession): Response {
        val context = getContext?.invoke() ?: return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No context"
        )

        return when {
            session.uri == "/ping" -> {
                newFixedLengthResponse("ControlDroid")
            }

            session.uri == "/action" && session.method == Method.POST -> {
                val body = session.parseBodyToString()

                DeviceAction.entries.find { it.command == body }?.let { action ->
                    Log.d("TargetHttpServer", "Performing action: ${action.name}")

                    when (action) {
                        DeviceAction.CAPTURE_SCREEN -> runBlocking {
                            val bitmap = ScreenCaptureManager.captureOnceSuspend(context)
                            if (bitmap != null) {
                                ScreenCaptureManager.saveBitmap(context, bitmap)
                            }
                        }

                        else -> AccessibilityController.performAction(action)
                    }
                }

                newFixedLengthResponse("OK")
            }
            session.uri == "/screenshot" && session.method == Method.GET -> {
                val file = File(context.cacheDir, "screenshot.png")
                return if (file.exists() && file.length() > 0) {
                    val stream = FileInputStream(file)
                    newChunkedResponse(Response.Status.OK, "image/png", stream)
                } else {
                    Log.w("TargetHttpServer", "Screenshot not available.")
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No Screenshot"
                    )
                }
            }

            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
            }
        }
    }

    private fun IHTTPSession.parseBodyToString(): String {
        val map = HashMap<String, String>()
        return try {
            parseBody(map)
            map["postData"] ?: ""
        } catch (e: Exception) {
            Log.e("TargetHttpServer", "Body parse error: ${e.message}")
            ""
        }
    }
}