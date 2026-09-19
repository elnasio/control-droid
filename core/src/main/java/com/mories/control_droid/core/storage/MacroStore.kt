package com.mories.control_droid.core.storage

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mories.control_droid.core.model.Macro

class MacroStore(context: Context) {
    private val prefs = context.getSharedPreferences("macros", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): List<Macro> {
        val json = prefs.getString(KEY_MACROS, "[]") ?: "[]"
        return runCatching {
            gson.fromJson<List<Macro>>(json, object : TypeToken<List<Macro>>() {}.type)
        }.getOrDefault(emptyList())
    }

    fun save(macro: Macro) {
        val current = getAll().filterNot { it.id == macro.id } + macro
        prefs.edit { putString(KEY_MACROS, gson.toJson(current)) }
    }

    fun delete(id: String) {
        prefs.edit { putString(KEY_MACROS, gson.toJson(getAll().filterNot { it.id == id })) }
    }

    private companion object {
        const val KEY_MACROS = "saved_macros"
    }
}
