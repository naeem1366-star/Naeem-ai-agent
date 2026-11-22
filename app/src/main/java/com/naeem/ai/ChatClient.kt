package com.naeem.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun send(baseUrl: String, model: String, apiKey: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "ابتدا کلید API را در تنظیمات وارد کن." }
            val root = baseUrl.trim().trimEnd('/')
            val payload = JSONObject()
                .put("model", model)
                .put("messages", org.json.JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt)
                ))
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$root/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                    val detail = message?.takeIf { it.isNotBlank() } ?: text.take(500).ifBlank { response.message }
                    throw IllegalStateException("خطای سرور HTTP ${response.code}: $detail")
                }
                val json = JSONObject(text)
                json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content", "پاسخی دریافت نشد.")
            }
        }
}
