package com.mories.control_droid.core.model

data class PairedDevice(
    val id: String, val name: String, val ip: String, val pin: String, val lastConnected: Long
)

data class ControlCommand(
    val pin: String, val action: DeviceAction, val payload: Map<String, String>? = null
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