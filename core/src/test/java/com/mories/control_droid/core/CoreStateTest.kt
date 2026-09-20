package com.mories.control_droid.core

import android.content.Context
import com.mories.control_droid.core.auth.ControllerIdentityStore
import com.mories.control_droid.core.auth.PairingApprovalGate
import com.mories.control_droid.core.auth.PairingTokenStore
import com.mories.control_droid.core.auth.PinVerifier
import com.mories.control_droid.core.auth.RoleManager
import com.mories.control_droid.core.auth.TrustedControllerStore
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.DeviceRole
import com.mories.control_droid.core.model.Macro
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.core.storage.MacroStore
import com.mories.control_droid.core.storage.PairedDeviceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class CoreStateTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        listOf(
            "security_prefs", "pairing_security", "device_role", "paired_devices", "macros",
            "controller_identity", "trusted_controllers"
        ).forEach { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test
    fun pinVerifier_supportsSetVerifyAndClear() {
        val verifier = PinVerifier(context)

        assertFalse(verifier.isPinSet())
        verifier.setPin("1234")

        assertTrue(verifier.isPinSet())
        assertTrue(verifier.verify("1234"))
        assertFalse(verifier.verify("4321"))

        verifier.clearPin()
        assertFalse(verifier.isPinSet())
    }

    @Test
    fun pairingTokenStore_keepsTokenAndInvalidatesRegeneratedToken() {
        val store = PairingTokenStore(context)
        val first = store.getOrCreateToken()

        assertEquals(first, store.getOrCreateToken())
        assertTrue(store.isValid(first))

        val regenerated = store.regenerateToken()
        assertNotEquals(first, regenerated)
        assertFalse(store.isValid(first))
        assertTrue(store.isValid(regenerated))
    }

    @Test
    fun roleManager_persistsSelectedRole() {
        val manager = RoleManager(context)

        assertFalse(manager.hasRole())
        manager.setRole(DeviceRole.TARGET)

        assertTrue(manager.hasRole())
        assertEquals(DeviceRole.TARGET, manager.getRole())
    }

    @Test
    fun pairedDeviceStore_upsertsAndRemovesDevices() {
        val store = PairedDeviceStore(context)
        val id = UUID.randomUUID().toString()
        val original = PairedDevice(id, "Target", "192.168.1.5", "1234", 1L)
        val updated = original.copy(name = "Updated Target", accessToken = "token")

        store.saveDevice(original)
        store.saveDevice(updated)

        assertEquals(listOf(updated), store.getAll())
        assertEquals(updated, store.getDeviceById(id))

        store.removeDevice(id)
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun pairedDeviceStore_reusesExistingIdWhenIpAlreadyPaired() {
        val store = PairedDeviceStore(context)
        val original = PairedDevice(
            id = UUID.randomUUID().toString(),
            name = "Target",
            ip = "192.168.1.5",
            pin = "1234",
            lastConnected = 1L
        )
        store.saveDevice(original)

        // Re-pairing the same IP with a fresh id (as a new QR/subnet scan always produces) must
        // update the existing row in place, not add a second entry for the same device.
        val rescanned = PairedDevice(
            id = UUID.randomUUID().toString(),
            name = "Target",
            ip = "192.168.1.5",
            pin = "",
            lastConnected = 2L,
            accessToken = "new-token"
        )
        val resolved = store.saveDevice(rescanned)

        assertEquals(original.id, resolved.id)
        assertEquals(1, store.getAll().size)
        assertEquals(resolved, store.getDeviceById(original.id))
    }

    @Test
    fun controllerIdentityStore_persistsSameIdAcrossInstances() {
        val id = ControllerIdentityStore(context).getOrCreateId()

        assertEquals(id, ControllerIdentityStore(context).getOrCreateId())
        assertTrue(id.isNotBlank())
    }

    @Test
    fun trustedControllerStore_trustsAndRevokesById() {
        val store = TrustedControllerStore(context)
        val id = UUID.randomUUID().toString()

        assertFalse(store.isTrusted(id))
        store.trust(id, "Pixel 3a")

        assertTrue(store.isTrusted(id))
        assertEquals(1, store.getAll().size)
        assertEquals("Pixel 3a", store.getAll().first().name)

        store.revoke(id)
        assertFalse(store.isTrusted(id))
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun pairingApprovalGate_returnsTrueWhenApproved() {
        val approvedFlag = AtomicBoolean(false)
        val awaitingRequest = CountDownLatch(1)

        val worker = Thread {
            approvedFlag.set(
                PairingApprovalGate.requestApproval("controller-1", "Pixel 3a", timeoutMs = 5_000)
            )
            awaitingRequest.countDown()
        }
        worker.start()

        // Wait for the request to actually be posted before approving it.
        var pending = PairingApprovalGate.pendingRequest.value
        val deadline = System.currentTimeMillis() + 2_000
        while (pending == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            pending = PairingApprovalGate.pendingRequest.value
        }
        assertNotNull(pending)
        assertEquals("controller-1", pending?.controllerId)

        PairingApprovalGate.approve()

        assertTrue(awaitingRequest.await(2, TimeUnit.SECONDS))
        assertTrue(approvedFlag.get())
        assertNull(PairingApprovalGate.pendingRequest.value)
    }

    @Test
    fun pairingApprovalGate_returnsFalseWhenRejected() {
        val approvedFlag = AtomicBoolean(true)
        val awaitingRequest = CountDownLatch(1)

        val worker = Thread {
            approvedFlag.set(
                PairingApprovalGate.requestApproval("controller-2", "Pixel 3a", timeoutMs = 5_000)
            )
            awaitingRequest.countDown()
        }
        worker.start()

        var pending = PairingApprovalGate.pendingRequest.value
        val deadline = System.currentTimeMillis() + 2_000
        while (pending == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            pending = PairingApprovalGate.pendingRequest.value
        }
        assertNotNull(pending)

        PairingApprovalGate.reject()

        assertTrue(awaitingRequest.await(2, TimeUnit.SECONDS))
        assertFalse(approvedFlag.get())
    }

    @Test
    fun pairingApprovalGate_returnsFalseOnTimeout() {
        val approved = PairingApprovalGate.requestApproval("controller-3", "Pixel 3a", timeoutMs = 100)

        assertFalse(approved)
        assertNull(PairingApprovalGate.pendingRequest.value)
    }

    @Test
    fun macroStore_replacesAndDeletesMacros() {
        val store = MacroStore(context)
        val id = UUID.randomUUID().toString()
        val first = Macro(id, "Navigation", listOf(DeviceAction.GLOBAL_HOME))
        val replacement = first.copy(actions = listOf(DeviceAction.GLOBAL_BACK, DeviceAction.GLOBAL_RECENT))

        store.save(first)
        store.save(replacement)
        assertEquals(listOf(replacement), store.getAll())

        store.delete(id)
        assertTrue(store.getAll().isEmpty())
    }
}
