package com.mories.control_droid.features.viewmodel

sealed class RemotePreviewEvent {
    data class StartPolling(val ip: String, val pin: String, val accessToken: String) : RemotePreviewEvent()
    data object StopPolling : RemotePreviewEvent()
}
