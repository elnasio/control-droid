package com.mories.control_droid.features.viewmodel

import com.mories.control_droid.core.networking.DeviceControlClient

sealed class RemotePreviewEvent {
    data class StartPolling(val client: DeviceControlClient) : RemotePreviewEvent()
    data object StopPolling : RemotePreviewEvent()
}
