package com.mories.control_droid.core.networking

import android.util.Log
import com.google.gson.Gson
import com.mories.control_droid.core.ConstantValue
import com.mories.control_droid.core.model.ClipboardRequest
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.GestureRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val PIN_HEADER = "X-Control-Pin"
private const val TOKEN_HEADER = "X-Control-Token"

class DeviceHttpClient(
    private val targetIp: String,
    private val targetPort: Int = ConstantValue.PORT_VALUE,
    private val pin: String,
    private val accessToken: String = "",
) {
    private val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
    private val gson = Gson()

    data class ScreenshotResult(val statusCode: Int, val bytes: ByteArray?)

    fun ping(onResult: (Boolean) -> Unit) {
        val request = Request.Builder().url("http://$targetIp:$targetPort/ping").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(it.isSuccessful) }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceHttpClient", "Ping failed: ${e.message}")
                onResult(false)
            }
        })
    }

    fun sendAction(action: DeviceAction) {
        sendAction(action) { }
    }

    fun sendAction(action: DeviceAction, onResult: (Boolean) -> Unit) {
        val requestBody = action.command.toRequestBody("text/plain".toMediaTypeOrNull())
        val request = authorizedRequest("/action")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    Log.d(
                        "DeviceHttpClient",
                        "Action sent: ${action.command}, response: ${it.code}"
                    )
                    onResult(it.isSuccessful)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceHttpClient", "Failed to send action: ${e.message}")
                onResult(false)
            }
        })
    }

    fun verifyPairing(token: String, onResult: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("http://$targetIp:$targetPort/pair")
            .addHeader(TOKEN_HEADER, token)
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(it.isSuccessful, it.body?.string()) }
            }

            override fun onFailure(call: Call, e: IOException) {
                onResult(false, e.message)
            }
        })
    }

    fun sendGesture(gesture: GestureRequest, onResult: (Boolean) -> Unit = {}) {
        sendJson("/gesture", gesture, onResult)
    }

    fun sendClipboard(requestBody: ClipboardRequest, onResult: (Boolean) -> Unit = {}) {
        sendJson("/clipboard", requestBody, onResult)
    }

    suspend fun fetchScreenshot(): ScreenshotResult = withContext(Dispatchers.IO) {
        val request = authorizedRequest("/screenshot").get().build()
        client.newCall(request).execute().use { response ->
            ScreenshotResult(
                statusCode = response.code,
                bytes = if (response.isSuccessful) response.body?.bytes() else null
            )
        }
    }

    private fun <T> sendJson(path: String, value: T, onResult: (Boolean) -> Unit) {
        val body = gson.toJson(value).toRequestBody("application/json".toMediaTypeOrNull())
        val request = authorizedRequest(path).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(it.isSuccessful) }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceHttpClient", "Request $path failed: ${e.message}")
                onResult(false)
            }
        })
    }

    private fun authorizedRequest(path: String): Request.Builder {
        val builder = Request.Builder().url("http://$targetIp:$targetPort$path")
        if (accessToken.isNotBlank()) builder.addHeader(TOKEN_HEADER, accessToken)
        if (pin.isNotBlank()) builder.addHeader(PIN_HEADER, pin)
        return builder
    }
}
