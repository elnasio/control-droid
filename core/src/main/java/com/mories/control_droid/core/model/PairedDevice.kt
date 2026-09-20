package com.mories.control_droid.core.model

data class PairedDevice(
    val id: String,
    val name: String,
    val ip: String,
    val pin: String,
    val lastConnected: Long,
    val accessToken: String? = null
)

enum class DeviceRole {
    CONTROLLER, TARGET
}

enum class DeviceAction(val command: String, val label: String, val emoji: String) {
    GLOBAL_BACK("global_back", "Back", "⬅️"), GLOBAL_HOME(
        "global_home", "Home", "🏠"
    ),
    GLOBAL_RECENT("global_recent", "Recent", "📋"), CAPTURE_SCREEN(
        "capture_screen", "Lihat Layar", "👀"
    );
}

data class PairingQrPayload(
    val version: Int = 1,
    val name: String,
    val ip: String,
    val port: Int,
    val token: String
) {
    companion object {
        private const val PREFIX = "controldroid://pair?"

        fun encode(payload: PairingQrPayload): String =
            PREFIX + listOf(
                "v=${payload.version}",
                "name=${java.net.URLEncoder.encode(payload.name, Charsets.UTF_8.name())}",
                "ip=${payload.ip}",
                "port=${payload.port}",
                "token=${payload.token}"
            ).joinToString("&")

        fun decode(value: String): PairingQrPayload? {
            if (!value.startsWith(PREFIX)) return null
            val values = value.removePrefix(PREFIX)
                .split('&')
                .mapNotNull { item ->
                    val separator = item.indexOf('=')
                    if (separator <= 0) null else item.substring(0, separator) to item.substring(separator + 1)
                }
                .toMap()
            return runCatching {
                PairingQrPayload(
                    version = values["v"]?.toInt() ?: 1,
                    name = java.net.URLDecoder.decode(values.getValue("name"), Charsets.UTF_8.name()),
                    ip = values.getValue("ip"),
                    port = values["port"]?.toInt() ?: com.mories.control_droid.core.ConstantValue.PORT_VALUE,
                    token = values.getValue("token")
                )
            }.getOrNull()
        }
    }
}

enum class GestureType { TAP, SWIPE }

data class GestureRequest(
    val type: GestureType,
    val startX: Float,
    val startY: Float,
    val endX: Float = startX,
    val endY: Float = startY,
    val durationMs: Long = 250
)

data class ClipboardRequest(val text: String, val paste: Boolean = false)

data class Macro(
    val id: String,
    val name: String,
    val actions: List<DeviceAction>
)

/** A Controller the Target has approved; remembered so it never has to ask for approval again. */
data class TrustedController(
    val id: String,
    val name: String,
    val approvedAt: Long
)

/** A pairing request currently waiting on the Target user to tap Terima/Tolak. */
data class PendingPairingRequest(
    val controllerId: String,
    val controllerName: String
)
