package com.mories.control_droid.core.networking

/** Raw screenshot response shared by every [DeviceControlClient] implementation. */
data class ScreenshotResult(val statusCode: Int, val bytes: ByteArray?)
