package com.mories.control_droid.core.networking

import android.util.Log
import com.google.gson.Gson
import com.mories.control_droid.core.model.ControlCommand
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetAddress

class LocalWebSocketServer(
    private val port: Int = 8080,
    private val pinVerifier: (String) -> Boolean,
    private val onCommand: (ControlCommand) -> Unit
) : NanoWSD(port) {

    private val gson = Gson()

    override fun openWebSocket(handshake: IHTTPSession?): WebSocket {
        return object : WebSocket(handshake) {
            override fun onOpen() {
                Log.i("WSS", "Client connected: ${this.isOpen}")
            }

            override fun onClose(
                code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean
            ) {
                Log.i("WSS", "Client disconnected: $reason")
            }

            override fun onMessage(message: WebSocketFrame?) {
                message?.textPayload?.let { raw ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val cmd = gson.fromJson(raw, ControlCommand::class.java)
                            if (pinVerifier(cmd.pin)) {
                                onCommand(cmd)
                            } else {
                                send("""{ "status": "error", "reason": "Invalid PIN" }""")
                            }
                        } catch (e: Exception) {
                            Log.e("WSS", "Parse error: $e")
                            send("""{ "status": "error", "reason": "Parse error" }""")
                        }
                    }
                }
            }

            override fun onPong(p0: WebSocketFrame?) {}
            override fun onException(exception: IOException?) {
                Log.e("WSS", "Error: ${exception?.message}")
            }

        }
    }

    fun startServer() {
        try {
            start()
            val ip = InetAddress.getLocalHost().hostAddress
            Log.i("WSS", "WebSocket Server started at ws://$ip:$port")
        } catch (e: Exception) {
            Log.e("WSS", "Failed to start server: $e")
        }
    }

    fun stopServer() {
        stop()
    }
}