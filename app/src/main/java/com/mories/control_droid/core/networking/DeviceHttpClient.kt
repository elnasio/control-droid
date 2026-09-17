package com.mories.control_droid.core.networking

import android.util.Log
import com.mories.control_droid.core.ConstantValue
import com.mories.control_droid.core.model.DeviceAction
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

class DeviceHttpClient(
    private val targetIp: String,
    private val targetPort: Int = ConstantValue.PORT_VALUE,
    private val pin: String,
) {
    private val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()

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
        val requestBody = action.command.toRequestBody("text/plain".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("http://$targetIp:$targetPort/action")
            .addHeader(PIN_HEADER, pin)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    Log.d(
                        "DeviceHttpClient",
                        "Action sent: ${action.command}, response: ${it.code}"
                    )
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceHttpClient", "Failed to send action: ${e.message}")
            }
        })
    }
}
