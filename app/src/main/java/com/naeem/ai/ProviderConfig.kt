package com.naeem.ai

data class ProviderConfig(
    val name: String,
    val baseUrl: String,
    val model: String
) {
    companion object {
        val presets = listOf(
            ProviderConfig("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            ProviderConfig("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
            ProviderConfig("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
            ProviderConfig("سفارشی", "https://your-endpoint.example/v1", "your-model")
        )
    }
}
