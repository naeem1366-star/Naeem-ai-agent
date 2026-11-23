package com.naeem.ai

data class ProviderConfig(
    val name: String,
    val baseUrl: String,
    val model: String
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.gapgpt.ir/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        // GapGPT is the default provider for this app. Model IDs must match
        // the IDs currently enabled in the user's GapGPT API dashboard.
        val presets = listOf(
            ProviderConfig("GapGPT", DEFAULT_BASE_URL, DEFAULT_MODEL),
            ProviderConfig("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            ProviderConfig("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
            ProviderConfig("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
            ProviderConfig("سفارشی", "https://your-endpoint.example/v1", "your-model")
        )
    }
}
