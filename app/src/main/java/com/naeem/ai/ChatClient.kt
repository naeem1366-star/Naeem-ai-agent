package com.naeem.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun send(baseUrl: String, model: String, apiKey: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "کلید API در تنظیمات وارد نشده است." }
            require(model.isNotBlank()) { "نام مدل در تنظیمات خالی است." }
            val root = baseUrl.trim().trimEnd('/')
            require(root.startsWith("https://", ignoreCase = true)) {
                "نشانی سرویس باید با https:// شروع شود. اتصال ناامن HTTP مجاز نیست."
            }
            val endpoint = "$root/chat/completions"
            val payload = JSONObject()
                .put("model", model.trim())
                .put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt)
                ))
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .post(body)
                .build()
            http.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val parsed = runCatching {
                        val json = JSONObject(responseText)
                        val error = json.opt("error")
                        when (error) {
                            is JSONObject -> error.optString("message").ifBlank { error.toString() }
                            is String -> error
                            else -> json.optString("message").ifBlank { json.toString() }
                        }
                    }.getOrNull()
                    val detail = parsed?.takeIf { it.isNotBlank() }
                        ?: responseText.take(700).ifBlank { response.message }
                    throw IllegalStateException(
                        "HTTP ${response.code} در آدرس:\n$endpoint\nجزئیات سرور: $detail"
                    )
                }
                val json = JSONObject(responseText)
                val content = json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").opt("content")
                when (content) {
                    is String -> content
                    is JSONArray -> buildString {
                        for (i in 0 until content.length()) {
                            val part = content.optJSONObject(i)
                            if (part != null) append(part.optString("text"))
                        }
                    }.ifBlank { "پاسخی دریافت نشد." }
                    else -> "پاسخی دریافت نشد."
                }
            }
        }
}
