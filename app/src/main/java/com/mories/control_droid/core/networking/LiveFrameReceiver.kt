package com.mories.control_droid.core.networking

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class LiveFrameReceiver(
    private val ip: String, private val port: Int = 8080
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect(onFrameReceived: (ByteArray) -> Unit) {
        val request = Request.Builder().url("ws://$ip:$port/ws").build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("FrameReceiver", "✅ Connected to $ip:$port")
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                onFrameReceived(bytes.toByteArray())
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d("FrameReceiver", "🔌 Disconnected: $reason")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("FrameReceiver", "❌ Failed: ${t.message}")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal Closure")
        webSocket = null
    }
}