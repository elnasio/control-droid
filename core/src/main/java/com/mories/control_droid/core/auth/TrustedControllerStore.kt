package com.mories.control_droid.core.auth

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mories.control_droid.core.model.TrustedController

/**
 * Target-side memory of which Controllers have already been approved, so an approved Controller
 * is recognized on later `/pair` calls instead of prompting for approval again.
 */
class TrustedControllerStore(context: Context) {
    private val prefs = context.getSharedPreferences("trusted_controllers", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun isTrusted(controllerId: String): Boolean =
        controllerId.isNotBlank() && getAll().any { it.id == controllerId }

    fun trust(controllerId: String, name: String) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == controllerId }
        current.add(TrustedController(controllerId, name, System.currentTimeMillis()))
        save(current)
    }

    fun revoke(controllerId: String) {
        save(getAll().filterNot { it.id == controllerId })
    }

    fun getAll(): List<TrustedController> {
        val json = prefs.getString(KEY_CONTROLLERS, "[]") ?: "[]"
        val type = object : TypeToken<List<TrustedController>>() {}.type
        return runCatching { gson.fromJson<List<TrustedController>>(json, type) }.getOrNull() ?: emptyList()
    }

    private fun save(controllers: List<TrustedController>) {
        prefs.edit { putString(KEY_CONTROLLERS, gson.toJson(controllers)) }
    }

    private companion object {
        const val KEY_CONTROLLERS = "controllers"
    }
}
