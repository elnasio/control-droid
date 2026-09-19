package com.mories.control_droid.core.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mories.control_droid.core.model.PairedDevice
import androidx.core.content.edit

class PairedDeviceStore(context: Context) {

    private val prefs = context.getSharedPreferences("paired_devices", Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Saves [device], returning the [PairedDevice] actually persisted. If another entry already
     * exists with the same IP (e.g. re-pairing the same physical Target after its role/token
     * changed), that entry's id is reused instead of adding a duplicate row for the same device.
     */
    fun saveDevice(device: PairedDevice): PairedDevice {
        val current = getAll().toMutableList()
        val existingByIp = current.find { it.ip == device.ip && it.id != device.id }
        val resolved = if (existingByIp != null) device.copy(id = existingByIp.id) else device
        current.removeAll { it.id == resolved.id }
        current.add(resolved)
        val json = gson.toJson(current)
        prefs.edit { putString("devices", json) }
        return resolved
    }

    fun getAll(): List<PairedDevice> {
        val json = prefs.getString("devices", "[]") ?: "[]"
        val type = object : TypeToken<List<PairedDevice>>() {}.type
        return gson.fromJson(json, type)
    }

    fun removeDevice(id: String) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == id }
        val json = gson.toJson(current)
        prefs.edit { putString("devices", json) }
    }

    fun getDeviceById(id: String): PairedDevice? {
        return getAll().find { it.id == id }
    }

    fun clearAll() {
        prefs.edit { remove("devices") }
    }
}