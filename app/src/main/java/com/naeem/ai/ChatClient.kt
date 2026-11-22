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

    suspend fun send(baseUrl: String, model: String, apiKey: String, prompt: String, imageBase64: String? = null): String =
        withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim().let { value ->
                if (value.startsWith("Bearer ", ignoreCase = true)) value.substringAfter(' ').trim()
                else value
            }
            require(cleanKey.isNotBlank()) { "کلید API در تنظیمات وارد نشده است." }
            require(cleanKey.all { it.code in 0x21..0x7E }) {
                "مقدار فیلد کلید API معتبر نیست؛ فقط کلید اصلی انگلیسی را از پنل GapGPT کپی کن. متن فارسی، فاصله یا توضیحات را وارد نکن."
            }
            require(model.isNotBlank()) { "شناسه مدل در تنظیمات خالی است. نام مدل را دقیقاً از پنل GapGPT کپی کنید." }

            var root = baseUrl.trim().trimEnd('/')
            require(root.startsWith("https://", ignoreCase = true)) {
                "نشانی سرویس باید با https:// شروع شود. اتصال ناامن HTTP مجاز نیست."
            }
            // Accept either the base URL or a full chat-completions URL.
            if (root.endsWith("/chat/completions", ignoreCase = true)) {
                root = root.removeSuffix("/chat/completions")
            }
            val endpoint = "$root/chat/completions"
            val userContent: Any = if (imageBase64.isNullOrBlank()) {
                prompt
            } else {
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", prompt.ifBlank { "این تصویر را بررسی و تحلیل کن." }))
                    .put(JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$imageBase64").put("detail", "auto")
                    ))
            }
            val payload = JSONObject()
                .put("model", model.trim())
                .put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", userContent)
                ))
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $cleanKey")
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
                    val hint = when (response.code) {
                        401 -> "\nراهنما: کلید باید از پنل GapGPT باشد؛ آن را بدون Bearer وارد کنید و اعتبار/وضعیت کلید را در پنل بررسی کنید."
                        403 -> "\nراهنما: دسترسی کلید یا اعتبار حساب GapGPT را بررسی کنید."
                        404 -> "\nراهنما: آدرس Base URL و شناسه مدل را بررسی کنید."
                        429 -> "\nراهنما: محدودیت درخواست یا اعتبار API را بررسی کنید."
                        else -> ""
                    }
                    throw IllegalStateException(
                        "HTTP ${response.code} در آدرس:\n$endpoint\nجزئیات سرور: $detail$hint"
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
