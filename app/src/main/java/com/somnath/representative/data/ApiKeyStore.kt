package com.somnath.representative.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyStore(context: Context) {
    private val prefs: SharedPreferences = createPreferences(context)

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_MOLTBOOK_API, apiKey.trim()).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_MOLTBOOK_API).apply()
    }

    fun getApiKey(): String? {
        val value = prefs.getString(KEY_MOLTBOOK_API, null)?.trim().orEmpty()
        return value.ifBlank { null }
    }

    private fun createPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // TODO(M3): remove this fallback once encrypted preferences are guaranteed in every build/device.
            context.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val ENCRYPTED_PREFS = "secure_config"
        private const val PLAIN_PREFS = "secure_config_fallback"
        private const val KEY_MOLTBOOK_API = "moltbook_api_key"
    }
}
