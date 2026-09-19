package com.mories.control_droid.core.auth

import android.content.Context
import com.mories.control_droid.core.model.DeviceRole
import androidx.core.content.edit

class RoleManager(context: Context) {
    private val prefs = context.getSharedPreferences("device_role", Context.MODE_PRIVATE)

    fun setRole(role: DeviceRole) {
        prefs.edit { putString("role", role.name) }
    }

    fun getRole(): DeviceRole {
        return prefs.getString("role", DeviceRole.CONTROLLER.name)?.let { DeviceRole.valueOf(it) }
            ?: DeviceRole.CONTROLLER
    }

    fun hasRole(): Boolean {
        return prefs.contains("role")
    }
}