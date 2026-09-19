package com.mories.control_droid.core.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mories.control_droid.core.ConstantValue
import com.mories.control_droid.core.auth.PinVerifier
import com.mories.control_droid.core.auth.PairingTokenStore
import com.mories.control_droid.core.control.AccessibilityController
import com.mories.control_droid.core.control.ClipboardController
import com.mories.control_droid.core.control.ScreenCaptureManager
import com.mories.control_droid.core.model.ClipboardRequest
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.GestureRequest
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
    private val gson = Gson()

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

            session.uri == "/pair" && session.method == Method.GET -> {
                val token = session.headerValue("X-Control-Token")
                if (!PairingTokenStore(context).isValid(token.orEmpty())) {
                    newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
                } else {
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"name\":\"ControlDroid\"}")
                }
            }

            session.uri == "/action" && session.method == Method.POST -> {
                if (!session.hasValidCredentials(context)) {
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
            session.uri == "/gesture" && session.method == Method.POST -> {
                if (!session.hasValidCredentials(context)) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
                }
                session.parseJson<GestureRequest>()?.let(AccessibilityController::performGesture)
                newFixedLengthResponse("OK")
            }
            session.uri == "/clipboard" && session.method == Method.POST -> {
                if (!session.hasValidCredentials(context)) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
                }
                session.parseJson<ClipboardRequest>()?.let { request ->
                    ClipboardController.setText(context, request.text)
                    if (request.paste) AccessibilityController.pasteClipboard()
                }
                newFixedLengthResponse("OK")
            }
            session.uri == "/screenshot" && session.method == Method.GET -> {
                if (!session.hasValidCredentials(context)) {
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

    private fun IHTTPSession.headerValue(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun IHTTPSession.providedPin(): String? = headerValue("X-Control-Pin")

    private fun IHTTPSession.providedToken(): String? = headerValue("X-Control-Token")

    private fun IHTTPSession.hasValidCredentials(context: Context): Boolean {
        val tokenValid = PairingTokenStore(context).isValid(providedToken().orEmpty())
        val pinVerifier = PinVerifier(context)
        val pinValid = pinVerifier.isPinSet() && pinVerifier.verify(providedPin().orEmpty())
        return tokenValid || pinValid
    }

    private inline fun <reified T> IHTTPSession.parseJson(): T? {
        val body = parseBodyToString()
        return runCatching { gson.fromJson(body, T::class.java) }.getOrNull()
    }
}
