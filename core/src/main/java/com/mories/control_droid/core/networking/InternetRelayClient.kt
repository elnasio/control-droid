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
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val PIN_HEADER = "X-Control-Pin"
private const val AUTH_HEADER = "Authorization"
private const val TAG = "InternetRelayClient"

// A relay hop adds latency on top of the Target's own response time, so this budget is more
// generous than DeviceHttpClient's 3s local-network timeout.
private const val RELAY_TIMEOUT_SECONDS = 8L

/**
 * Client-side implementation of the cloud relay contract documented in
 * docs/internet-relay-api.md. The backend itself does not exist yet — this class is "ready to
 * wire": it speaks the full contract (paths, headers, JSON bodies) against [baseUrl], so once a
 * real relay is deployed there, switching a device to Internet mode starts working with no
 * further app changes. Until then every call fails with a network/connection error, which the UI
 * already treats the same as an unreachable Wi-Fi Target ("Tidak terhubung").
 *
 * Routing key: [deviceId] is the same id used locally for [PairedDevice][com.mories.control_droid.core.model.PairedDevice.id].
 * The contract assumes the (not-yet-built) pairing backend will key relay sessions by that same
 * id; if a future backend issues its own separate routing id instead, only this class's
 * [authorizedRequest] needs to change.
 */
class InternetRelayClient(
    private val deviceId: String,
    private val pin: String,
    private val accessToken: String = "",
    private val baseUrl: String = ConstantValue.INTERNET_RELAY_BASE_URL,
) : DeviceControlClient {

    private val client = OkHttpClient.Builder().callTimeout(RELAY_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    private val gson = Gson()

    override fun checkStatus(onResult: (Boolean) -> Unit) {
        val request = authorizedRequest("/status").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(it.isSuccessful) }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Relay status check failed: ${e.message}")
                onResult(false)
            }
        })
    }

    override fun sendAction(action: DeviceAction, onResult: (Boolean) -> Unit) {
        sendJson("/action", RelayActionBody(action.command), onResult)
    }

    override fun sendGesture(gesture: GestureRequest, onResult: (Boolean) -> Unit) {
        sendJson("/gesture", gesture, onResult)
    }

    override fun sendClipboard(request: ClipboardRequest, onResult: (Boolean) -> Unit) {
        sendJson("/clipboard", request, onResult)
    }

    override suspend fun fetchScreenshot(): ScreenshotResult = withContext(Dispatchers.IO) {
        val request = authorizedRequest("/screenshot").get().build()
        client.newCall(request).execute().use { response ->
            ScreenshotResult(
                statusCode = response.code,
                bytes = if (response.isSuccessful) response.body?.bytes() else null
            )
        }
    }

    private fun <T> sendJson(path: String, value: T, onResult: (Boolean) -> Unit) {
        val body: RequestBody = gson.toJson(value).toRequestBody("application/json".toMediaTypeOrNull())
        val request = authorizedRequest(path).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(it.isSuccessful) }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Relay request $path failed: ${e.message}")
                onResult(false)
            }
        })
    }

    private fun authorizedRequest(path: String): Request.Builder {
        val builder = Request.Builder().url("$baseUrl/v1/devices/$deviceId$path")
        if (accessToken.isNotBlank()) builder.addHeader(AUTH_HEADER, "Bearer $accessToken")
        if (pin.isNotBlank()) builder.addHeader(PIN_HEADER, pin)
        return builder
    }

    private data class RelayActionBody(val command: String)
}
