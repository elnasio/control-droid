package com.mories.control_droid.core.control

import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.Macro
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.core.networking.DeviceHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object MacroRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun runOnDevices(macro: Macro, devices: List<PairedDevice>, onComplete: () -> Unit = {}) {
        devices.forEach { device ->
            scope.launch {
                val client = DeviceHttpClient(
                    device.ip,
                    pin = device.pin,
                    accessToken = device.accessToken.orEmpty()
                )
                macro.actions.forEach { action ->
                    sendAction(client, action)
                    delay(250)
                }
            }
        }
        scope.launch {
            delay((macro.actions.size * 250L).coerceAtLeast(250L))
            onComplete()
        }
    }

    fun broadcastAction(action: DeviceAction, devices: List<PairedDevice>) {
        devices.forEach { device ->
            DeviceHttpClient(
                device.ip,
                pin = device.pin,
                accessToken = device.accessToken.orEmpty()
            ).sendAction(action)
        }
    }

    private suspend fun sendAction(client: DeviceHttpClient, action: DeviceAction): Boolean =
        suspendCancellableCoroutine { continuation ->
            client.sendAction(action) { success ->
                if (continuation.isActive) continuation.resume(success)
            }
        }
}
