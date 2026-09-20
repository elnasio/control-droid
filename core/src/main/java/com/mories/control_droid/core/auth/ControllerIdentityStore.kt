package com.mories.control_droid.core.auth

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import java.util.UUID

/**
 * Persists a stable identity for this Controller installation, so a Target can remember it as a
 * trusted Controller across pairing attempts instead of asking for approval every time.
 */
class ControllerIdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences("controller_identity", Context.MODE_PRIVATE)

    fun getOrCreateId(): String =
        prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also { id ->
            prefs.edit { putString(KEY_ID, id) }
        }

    fun displayName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private companion object {
        const val KEY_ID = "controller_id"
    }
}
