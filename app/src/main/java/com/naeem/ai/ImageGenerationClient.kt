package com.naeem.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ImageGenerationClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generate(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    ): Bitmap = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().let {
            if (it.startsWith("Bearer ", ignoreCase = true)) it.substringAfter(' ').trim() else it
        }
        require(cleanKey.isNotBlank()) { "کلید API در تنظیمات وارد نشده است." }
            require(cleanKey.all { it.code in 0x21..0x7E }) {
                "مقدار فیلد کلید API معتبر نیست؛ فقط کلید اصلی انگلیسی را از پنل GapGPT کپی کن. متن فارسی، فاصله یا توضیحات را وارد نکن."
            }
        require(prompt.isNotBlank()) { "توضیح تصویر را بنویس." }

        var root = baseUrl.trim().trimEnd('/')
        require(root.startsWith("https://", ignoreCase = true)) {
            "نشانی سرویس باید با https:// شروع شود."
        }
        if (root.endsWith("/images/generations", ignoreCase = true)) {
            root = root.removeSuffix("/images/generations")
        }
        val endpoint = "$root/images/generations"
        val payload = JSONObject()
            .put("model", model.trim())
            .put("prompt", prompt.trim())
            .put("n", 1)
            .put("size", "1024x1024")
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $cleanKey")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    val json = JSONObject(responseText)
                    val err = json.opt("error")
                    if (err is JSONObject) err.optString("message").ifBlank { err.toString() }
                    else json.optString("message").ifBlank { responseText.take(600) }
                }.getOrDefault(responseText.take(600))
                val hint = when (response.code) {
                    401 -> " کلید API را بررسی کن."
                    403 -> " دسترسی ساخت تصویر یا اعتبار حساب فعال نیست."
                    404 -> " مسیر یا مدل ساخت تصویر در سرویس پیدا نشد."
                    429 -> " محدودیت درخواست یا اعتبار API را بررسی کن."
                    else -> ""
                }
                throw IllegalStateException("خطای ساخت تصویر (HTTP ${response.code}): $detail$hint")
            }

            val responseJson = JSONObject(responseText)
            val data = responseJson.optJSONArray("data") ?: responseJson.optJSONArray("images")
                ?: throw IllegalStateException("پاسخ سرویس ساخت تصویر، آرایه data یا images ندارد: ${responseText.take(500)}")
            if (data.length() == 0) throw IllegalStateException("سرویس تصویری تولید نکرد.")
            val item = data.getJSONObject(0)
            val encodedImage = item.optString("b64_json").ifBlank {
                item.optString("image_base64")
            }.takeIf { it.isNotBlank() }
            val imageUrl = item.optString("url").ifBlank {
                item.optString("image_url")
            }.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            val bytes = encodedImage?.let { encoded ->
                val cleanBase64 = encoded.substringAfter("base64,", encoded)
                Base64.decode(cleanBase64, Base64.DEFAULT)
            } ?: imageUrl?.let { url ->
                val imageRequest = Request.Builder().url(url).get().build()
                http.newCall(imageRequest).execute().use { imageResponse ->
                    if (!imageResponse.isSuccessful) throw IllegalStateException("دریافت تصویر تولیدشده ناموفق بود (HTTP ${imageResponse.code}).")
                    imageResponse.body?.bytes() ?: throw IllegalStateException("فایل تصویر خالی است.")
                }
            } ?: throw IllegalStateException("سرویس پاسخ داده، اما لینک یا داده تصویر در خروجی پیدا نشد: ${item.toString().take(500)}")

            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("فایل برگشتی تصویر قابل نمایش نیست.")
        }
    }
}
