package com.mories.control_droid.core.auth

import android.content.Context
import android.content.SharedPreferences

class PinVerifier(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN = "secure_pin"
    }

    /**
     * Simpan PIN baru
     */
    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    /**
     * Ambil PIN yang tersimpan
     */
    fun getPin(): String? {
        return prefs.getString(KEY_PIN, null)
    }

    /**
     * Verifikasi input PIN terhadap PIN tersimpan
     */
    fun verify(inputPin: String): Boolean {
        val stored = getPin()
        return stored != null && stored == inputPin
    }

    /**
     * Cek apakah PIN sudah diatur
     */
    fun isPinSet(): Boolean {
        return getPin() != null
    }

    /**
     * Hapus PIN jika perlu reset
     */
    fun clearPin() {
        prefs.edit().remove(KEY_PIN).apply()
    }
}