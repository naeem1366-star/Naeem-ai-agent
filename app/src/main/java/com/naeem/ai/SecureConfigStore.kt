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
        val normalized = baseUrl.trim().trimEnd('/')
        val cleanKey = apiKey.trim().removeBearerPrefix()
        prefs.edit()
            .putString("base_url", normalized)
            .putString("model", model.trim())
            .putString("api_key", cleanKey)
            .putString(keyFor(normalized), cleanKey)
            .apply()
    }

    private fun keyFor(baseUrl: String): String =
        "api_key_" + baseUrl.trim().trimEnd('/').lowercase().hashCode().toUInt().toString(16)

    fun apiKeyFor(baseUrl: String): String {
        val saved = prefs.getString(keyFor(baseUrl), null)
        return saved ?: if (baseUrl.trim().trimEnd('/') == baseUrl()) apiKey() else ""
    }

    fun rememberApiKey(baseUrl: String, apiKey: String) {
        val clean = apiKey.trim().removeBearerPrefix()
        if (clean.isNotBlank()) prefs.edit().putString(keyFor(baseUrl), clean).apply()
    }

    fun modelFor(type: String): String = prefs.getString("model_$type", "").orEmpty()

    fun saveModelFor(type: String, model: String) {
        if (type.isNotBlank() && model.isNotBlank()) prefs.edit().putString("model_$type", model.trim()).apply()
    }

    fun speechModel(): String = modelFor("audio_tts").ifBlank { "gpt-4o-mini-tts" }

    fun baseUrl(): String {
        val saved = prefs.getString("base_url", null)?.trim()?.trimEnd('/')
        // Migrate existing installs that still have the old OpenAI default.
        if (saved.isNullOrBlank() || saved == "https://api.openai.com/v1" || saved == "https://api.gapgpt.app/v1" || saved == "https://api.gapgpt.ir/v1") {
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
