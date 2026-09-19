package com.mories.control_droid.core.auth

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom
import java.util.Base64

class PairingTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("pairing_security", Context.MODE_PRIVATE)

    fun getOrCreateToken(): String {
        return prefs.getString(KEY_TOKEN, null) ?: generateToken().also { token ->
            prefs.edit { putString(KEY_TOKEN, token) }
        }
    }

    fun regenerateToken(): String = generateToken().also { token ->
        prefs.edit { putString(KEY_TOKEN, token) }
    }

    fun isValid(candidate: String): Boolean = candidate.isNotBlank() && candidate == getOrCreateToken()

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val TOKEN_BYTES = 32
    }
}
