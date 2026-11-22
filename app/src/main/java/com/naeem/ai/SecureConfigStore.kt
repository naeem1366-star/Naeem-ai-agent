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
        prefs.edit()
            .putString("base_url", baseUrl.trim().trimEnd('/'))
            .putString("model", model.trim())
            .putString("api_key", apiKey.trim().removeBearerPrefix())
            .apply()
    }

    fun baseUrl(): String {
        val saved = prefs.getString("base_url", null)?.trim()?.trimEnd('/')
        // Migrate existing installs that still have the old OpenAI default.
        if (saved.isNullOrBlank() || saved == "https://api.openai.com/v1") {
            prefs.edit().putString("base_url", ProviderConfig.DEFAULT_BASE_URL).apply()
            return ProviderConfig.DEFAULT_BASE_URL
        }
        return saved
    }

    fun model(): String =
        prefs.getString("model", ProviderConfig.DEFAULT_MODEL)
            ?.takeIf { it.isNotBlank() }
            ?: ProviderConfig.DEFAULT_MODEL

    fun apiKey(): String = prefs.getString("api_key", "") ?: ""

    fun imageModel(): String = prefs.getString("image_model", "gpt-image-2") ?: "gpt-image-2"

    fun saveImageModel(model: String) {
        prefs.edit().putString("image_model", model.trim()).apply()
    }
}

private fun String.removeBearerPrefix(): String =
    trim().let { value ->
        if (value.startsWith("Bearer ", ignoreCase = true)) value.substringAfter(' ').trim()
        else value
    }
