package com.mories.control_droid.core.networking

import com.mories.control_droid.core.model.ClipboardRequest
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.GestureRequest

/**
 * Transport-agnostic contract for sending control commands to a Target and reading back its
 * screen. [DeviceHttpClient] implements this over the local Wi-Fi HTTP server; [InternetRelayClient]
 * implements the same contract over a cloud relay for when the Controller and Target are not on
 * the same network. UI code should depend on this interface rather than a concrete transport, so
 * switching between Wi-Fi and Internet control is just swapping which implementation is active.
 */
interface DeviceControlClient {
    fun checkStatus(onResult: (Boolean) -> Unit)
    fun sendAction(action: DeviceAction, onResult: (Boolean) -> Unit = {})
    fun sendGesture(gesture: GestureRequest, onResult: (Boolean) -> Unit = {})
    fun sendClipboard(request: ClipboardRequest, onResult: (Boolean) -> Unit = {})
    suspend fun fetchScreenshot(): ScreenshotResult
}
