package com.mories.control_droid.core

import android.content.Context
import com.mories.control_droid.core.auth.PairingTokenStore
import com.mories.control_droid.core.auth.PinVerifier
import com.mories.control_droid.core.auth.RoleManager
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.DeviceRole
import com.mories.control_droid.core.model.Macro
import com.mories.control_droid.core.model.PairedDevice
import com.mories.control_droid.core.storage.MacroStore
import com.mories.control_droid.core.storage.PairedDeviceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class CoreStateTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        listOf("security_prefs", "pairing_security", "device_role", "paired_devices", "macros")
            .forEach { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
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
