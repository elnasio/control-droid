package com.mories.control_droid.core.server

import android.content.Context
import android.util.Log
import com.mories.control_droid.core.ConstantValue
import com.mories.control_droid.core.auth.PinVerifier
import com.mories.control_droid.core.control.AccessibilityController
import com.mories.control_droid.core.control.ScreenCaptureManager
import com.mories.control_droid.core.model.DeviceAction
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton HTTP server yang berjalan di device TARGET.
 * Berfungsi untuk merespons scan dari device CONTROLLER melalui endpoint /ping.
 */
object TargetHttpServer : NanoHTTPD(ConstantValue.PORT_VALUE) {

    private val isRunning = AtomicBoolean(false)
    private var getContext: (() -> Context)? = null

    fun startServer(getContextProvider: () -> Context) {
        if (isRunning.get()) return

        getContext = getContextProvider
        try {
            start(SOCKET_READ_TIMEOUT, false)
            isRunning.set(true)
            Log.d("TargetHttpServer", "✅ Started on port ${ConstantValue.PORT_VALUE}")
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
                if (!session.hasValidPin(context)) {
                    return newFixedLengthResponse(
                        Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized"
                    )
                }
                val body = session.parseBodyToString()

                DeviceAction.entries.find { it.command == body }?.let { action ->
                    Log.d("TargetHttpServer", "Performing action: ${action.name}")

                    when (action) {
                        DeviceAction.CAPTURE_SCREEN -> {
                            CoroutineScope(Dispatchers.Default).launch {
                                val bitmap = ScreenCaptureManager.captureOnceSuspend(context)
                                if (bitmap != null) {
                                    ScreenCaptureManager.saveBitmap(context, bitmap)
                                }
                            }
                        }
                        else -> AccessibilityController.performAction(action)
                    }
                }

                newFixedLengthResponse("OK")
            }
            session.uri == "/screenshot" && session.method == Method.GET -> {
                if (!session.hasValidPin(context)) {
                    return newFixedLengthResponse(
                        Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized"
                    )
                }
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

    private fun IHTTPSession.providedPin(): String? =
        headers.entries.firstOrNull { it.key.equals("X-Control-Pin", ignoreCase = true) }?.value

    private fun IHTTPSession.hasValidPin(context: Context): Boolean {
        val verifier = PinVerifier(context)
        return verifier.isPinSet() && verifier.verify(providedPin().orEmpty())
    }
}