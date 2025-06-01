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
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.concurrent.TimeUnit

class LocalWebSocketClient(
    private val targetIp: String,
    private val targetPort: Int = 8080,
    private val pin: String,
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder().pingInterval(10, TimeUnit.SECONDS).build()

    private var isConnected = false

    fun connect(onResponse: (String) -> Unit = {}, onConnected: () -> Unit = {}) {
        val url = "ws://$targetIp:$targetPort/ws"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i("WSC", "✅ Connected to $url")
                isConnected = true
                // Kirim PIN jika dibutuhkan autentikasi awal
                ws.send("AUTH:$pin")
                onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.i("WSC", "📨 Received: $text")
                onResponse(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                Log.i("WSC", "📨 Received binary message")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("WSC", "❌ Connection failed: ${t.message}")
                isConnected = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i("WSC", "🔌 Disconnected: $reason")
                isConnected = false
            }
        })
    }

    fun sendAction(action: DeviceAction) {
        if (isConnected && webSocket != null) {
            webSocket?.send(action.command)
        } else {
            val requestBody = action.command.toRequestBody("text/plain".toMediaTypeOrNull())
            val request =
                Request.Builder().url("http://$targetIp:${ConstantValue.PORT_VALUE}/action").post(requestBody).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    Log.d(
                        "LocalWebSocketClient",
                        "Action sent: ${action.command}, response: ${response.code}"
                    )
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("LocalWebSocketClient", "Failed to send action: ${e.message}")
                }
            })
        }
    }

    fun close() {
        webSocket?.close(1000, "Normal Closure")
        webSocket = null
        isConnected = false
    }
}