package com.mories.control_droid.core.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mories.control_droid.core.model.PairedDevice
import androidx.core.content.edit

class PairedDeviceStore(context: Context) {

    private val prefs = context.getSharedPreferences("paired_devices", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveDevice(device: PairedDevice) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == device.id }
        current.add(device)
        val json = gson.toJson(current)
        prefs.edit { putString("devices", json) }
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