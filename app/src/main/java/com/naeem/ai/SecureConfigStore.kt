package com.naeem.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureConfigStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "naeem_ai_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(baseUrl: String, model: String, apiKey: String) {
        prefs.edit().putString("base_url", baseUrl)
            .putString("model", model).putString("api_key", apiKey).apply()
    }

    fun baseUrl() = prefs.getString("base_url", ProviderConfig.presets[0].baseUrl) ?: ProviderConfig.presets[0].baseUrl
    fun model() = prefs.getString("model", ProviderConfig.presets[0].model) ?: ProviderConfig.presets[0].model
    fun apiKey() = prefs.getString("api_key", "") ?: ""
}
