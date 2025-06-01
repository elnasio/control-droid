package com.mories.control_droid.core.networking

import fi.iki.elonen.NanoHTTPD

class TargetWebServer : NanoHTTPD(8080) {
    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/ping" -> newFixedLengthResponse("ControlDroid")
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found"
            )
        }
    }
}