package com.naeem.ai

data class ParsedApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val chatModel: String?,
    val imageModel: String?,
    val videoModel: String?,
    val speechModel: String?
)

/**
 * Local-only parser for explicitly labelled connection details pasted into Supra's chat.
 * Never logs or formats secret values for display.
 */
object ApiConfigTextParser {
    private val aliases = mapOf(
        "base_url" to listOf("base_url", "base url", "server_url", "server url", "api_url", "api url", "endpoint", "آدرس سرور", "آدرس api", "سرور"),
        "api_key" to listOf("api_key", "api key", "apikey", "secret_key", "secret key", "کلید api", "کلید", "توکن"),
        "chat_model" to listOf("chat_model", "chat model", "model", "مدل چت", "مدل"),
        "image_model" to listOf("image_model", "image model", "مدل تصویر"),
        "video_model" to listOf("video_model", "video model", "مدل ویدیو"),
        "speech_model" to listOf("speech_model", "tts_model", "audio_tts_model", "speech model", "مدل صدا", "مدل گفتار")
    )

    fun parse(raw: String): ParsedApiConfig? {
        if (raw.length > 12000) return null
        val values = mutableMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val clean = line.trim().trim(',', ';')
            val separator = clean.indexOfFirst { it == ':' || it == '=' }
            if (separator <= 0) return@forEach
            val label = clean.substring(0, separator).trim().trim('"', '\'', '`', ' ')
                .lowercase()
            val value = clean.substring(separator + 1).trim().trim(',', ';').trim().trim('"', '\'', '`', ' ')
            if (value.isBlank()) return@forEach
            val field = aliases.entries.firstOrNull { (_, names) -> names.any { it == label } }?.key
            if (field != null) values[field] = value
        }

        // Also accept a one-line Authorization: Bearer <key> header.
        if (values["api_key"].isNullOrBlank()) {
            val auth = Regex("(?im)^\\s*authorization\\s*:\\s*bearer\\s+([^\\s,}]+)").find(raw)
            auth?.groupValues?.getOrNull(1)?.let { values["api_key"] = it.trim('"', '\'') }
        }

        val url = values["base_url"]?.trimEnd('/') ?: return null
        val key = values["api_key"]?.removePrefix("Bearer ")?.trim() ?: return null
        if (!url.startsWith("https://", true) || key.length < 8) return null
        return ParsedApiConfig(
            baseUrl = url,
            apiKey = key,
            chatModel = values["chat_model"] ?: values["model"],
            imageModel = values["image_model"],
            videoModel = values["video_model"],
            speechModel = values["speech_model"]
        )
    }
}
