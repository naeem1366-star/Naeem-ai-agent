package com.naeem.ai

data class ProviderConfig(
    val name: String,
    val baseUrl: String,
    val model: String
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://netarz.ir/api/ai/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        // NetArz exposes an OpenAI-compatible gateway for chat and multimodal endpoints.
        val presets = listOf(
            ProviderConfig("NetArz", DEFAULT_BASE_URL, DEFAULT_MODEL),
            ProviderConfig("GapGPT", "https://api.gapgpt.ir/v1", DEFAULT_MODEL),
            ProviderConfig("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            ProviderConfig("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
            ProviderConfig("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
            ProviderConfig("سفارشی", "https://your-endpoint.example/v1", "your-model")
        )
    }
}
