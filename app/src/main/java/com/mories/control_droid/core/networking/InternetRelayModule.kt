package com.mories.control_droid.core.networking

import android.util.Log
import com.google.gson.Gson
import com.mories.control_droid.core.model.ControlCommand
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class InternetRelayModule(
    private val deviceId: String,
    private val relayUrl: String = "wss://your-relay-server.com/ws", // ganti sesuai server-mu
    private val onCommandReceived: (ControlCommand) -> Unit
) {
    private val gson = Gson()
    private val client = OkHttpClient()
    private var socket: WebSocket? = null

    fun connect() {
        val request = Request.Builder().url(relayUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i("InternetRelay", "Connected to relay server")
                ws.send(gson.toJson(mapOf("type" to "register", "id" to deviceId)))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val wrapper = gson.fromJson(text, RelayPayload::class.java)
                    if (wrapper.to == deviceId && wrapper.command != null) {
                        onCommandReceived(wrapper.command)
                    }
                } catch (e: Exception) {
                    Log.e("InternetRelay", "Parse error: $e")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("InternetRelay", "Failed: ${t.message}")
            }
        })
    }

    fun sendCommand(to: String, command: ControlCommand) {
        val payload = RelayPayload(
            from = deviceId,
            to = to,
            command = command
        )
        val json = gson.toJson(payload)
        socket?.send(json)
    }

    fun disconnect() {
        socket?.close(1000, "Closing")
        socket = null
    }

    data class RelayPayload(
        val from: String,
        val to: String,
        val command: ControlCommand?
    )
}