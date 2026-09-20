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
private const val CONTROLLER_ID_HEADER = "X-Controller-Id"
private const val CONTROLLER_NAME_HEADER = "X-Controller-Name"

// The Target may hold a /pair request open while it waits for a human tap on Terima/Tolak
// (see PairingApprovalGate, up to 45s), so pairing calls need a much longer timeout than the
// few-second budget that's appropriate for a regular action/gesture call.
private const val PAIRING_TIMEOUT_SECONDS = 50L

class DeviceHttpClient(
    private val targetIp: String,
    private val targetPort: Int = ConstantValue.PORT_VALUE,
    private val pin: String,
    private val accessToken: String = "",
    private val controllerId: String = "",
    private val controllerName: String = "",
) : DeviceControlClient {
    private val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
    private val pairingClient = OkHttpClient.Builder().callTimeout(PAIRING_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    private val gson = Gson()

    /**
     * Checks whether this client's stored PIN/token would actually authorize a real
     * action/gesture/screenshot call right now. Unlike a bare /ping (which only proves the
     * Target's server is reachable), this reports "connected" only when credentials are
     * genuinely valid, so the Controller UI never shows Terhubung for a device whose PIN/token
     * has since changed or been revoked.
     */
    override fun checkStatus(onResult: (Boolean) -> Unit) {
        val request = authorizedRequest("/status").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(it.isSuccessful) }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceHttpClient", "Status check failed: ${e.message}")
                onResult(false)
            }
        })
    }

    fun sendAction(action: DeviceAction) {
        sendAction(action) { }
    }

    override fun sendAction(action: DeviceAction, onResult: (Boolean) -> Unit) {
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
            .applyControllerIdentity()
            .get()
            .build()
        pairingClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(it.isSuccessful, it.body?.string()) }
            }

            override fun onFailure(call: Call, e: IOException) {
                onResult(false, e.message)
            }
        })
    }

    /**
     * Validates [pin] against the Target's /pair endpoint before it's saved as a paired device,
     * mirroring [verifyPairing] for the legacy PIN flow so a wrong PIN is rejected immediately
     * instead of being silently stored and only failing on the first real action later.
     */
    fun verifyPin(pin: String, onResult: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("http://$targetIp:$targetPort/pair")
            .addHeader(PIN_HEADER, pin)
            .applyControllerIdentity()
            .get()
            .build()
        pairingClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(it.isSuccessful, it.body?.string()) }
            }

            override fun onFailure(call: Call, e: IOException) {
                onResult(false, e.message)
            }
        })
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

    private fun Request.Builder.applyControllerIdentity(): Request.Builder = apply {
        if (controllerId.isNotBlank()) addHeader(CONTROLLER_ID_HEADER, controllerId)
        if (controllerName.isNotBlank()) addHeader(CONTROLLER_NAME_HEADER, controllerName)
    }
}
