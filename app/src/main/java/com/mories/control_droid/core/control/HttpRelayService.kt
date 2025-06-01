package com.mories.control_droid.core.control

import android.util.Log
import com.mories.control_droid.core.ConstantValue
import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.URL

class HttpRelayService : NanoHTTPD(ConstantValue.PORT_VALUE) {

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.uri == "/screenshot" -> {
                try {
                    val url = URL("http://10.0.2.16:8080/screenshot")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connect()

                    if (conn.responseCode == 200) {
                        val inputStream = conn.inputStream
                        newChunkedResponse(Response.Status.OK, conn.contentType, inputStream)
                    } else {
                        newFixedLengthResponse("Relay failed: ${conn.responseCode}")
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message
                    )
                }
            }

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404")
        }
    }

    companion object {
        private var instance: HttpRelayService? = null

        fun start() {
            if (instance == null) {
                instance = HttpRelayService()
                instance?.start()
                Log.d("RelayService", "🚀 Relay started on port ${ConstantValue.PORT_VALUE}")
            }
        }

        fun stop() {
            instance?.stop()
            instance = null
            Log.d("RelayService", "🛑 Relay stopped")
        }
    }
}