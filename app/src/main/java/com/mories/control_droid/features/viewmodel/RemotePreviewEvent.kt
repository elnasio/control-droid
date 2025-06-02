package com.mories.control_droid.features.viewmodel

sealed class RemotePreviewEvent {
    data class StartPolling(val ip: String) : RemotePreviewEvent()
    data object StopPolling : RemotePreviewEvent()
}