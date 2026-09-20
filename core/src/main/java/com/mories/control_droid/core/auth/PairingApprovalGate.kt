package com.mories.control_droid.core.auth

import com.mories.control_droid.core.model.PendingPairingRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Bridges an incoming `/pair` request (running on a NanoHTTPD worker thread, one thread per
 * connection) with the Target's Compose UI (main thread), so pairing from an unrecognized
 * Controller blocks waiting for a human tap on Terima/Tolak instead of succeeding silently.
 *
 * Only one approval is shown at a time: a second concurrent request simply waits its turn on
 * [lock] before displaying its own prompt.
 */
object PairingApprovalGate {
    private val lock = ReentrantLock()

    private val _pendingRequest = MutableStateFlow<PendingPairingRequest?>(null)
    val pendingRequest: StateFlow<PendingPairingRequest?> = _pendingRequest.asStateFlow()

    @Volatile
    private var latch: CountDownLatch? = null

    @Volatile
    private var approved: Boolean = false

    /**
     * Blocks the calling thread until the user approves/rejects the request or [timeoutMs]
     * elapses (auto-rejecting on timeout). Safe to call from an HTTP worker thread.
     */
    fun requestApproval(controllerId: String, controllerName: String, timeoutMs: Long): Boolean = lock.withLock {
        val requestLatch = CountDownLatch(1)
        latch = requestLatch
        approved = false
        _pendingRequest.value = PendingPairingRequest(controllerId, controllerName)

        requestLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

        _pendingRequest.value = null
        latch = null
        approved
    }

    /** Call from the UI thread when the user taps Terima. */
    fun approve() {
        approved = true
        latch?.countDown()
    }

    /** Call from the UI thread when the user taps Tolak. */
    fun reject() {
        approved = false
        latch?.countDown()
    }
}
