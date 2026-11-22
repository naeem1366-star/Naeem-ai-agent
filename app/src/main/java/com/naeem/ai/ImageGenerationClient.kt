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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class ImageGenerationClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
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
            .put("output_format", "png")

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

            val responseJson = runCatching { JSONObject(responseText) }.getOrElse {
                throw IllegalStateException("پاسخ سرویس تصویر JSON معتبر نیست: ${responseText.take(300)}")
            }
            val imageItem = findImageObject(responseJson)
                ?: throw IllegalStateException("پاسخ سرویس تصویر، داده یا لینک تصویر ندارد. کلیدهای پاسخ: ${responseJson.keys().asSequence().toList()}")

            val encodedImage = firstString(
                imageItem,
                "b64_json", "image_base64", "base64", "image"
            )?.substringAfter("base64,", firstString(imageItem, "b64_json", "image_base64", "base64", "image") ?: "")
                ?.takeIf { it.isNotBlank() }

            val imageUrl = firstString(imageItem, "url", "image_url", "imageUrl")
                ?.let { candidate ->
                    candidate.replace("\\/", "/").trim()
                }
                ?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }

            val bytes = when {
                !encodedImage.isNullOrBlank() -> decodeBase64(encodedImage)
                    ?: throw IllegalStateException("داده Base64 برگشتی تصویر قابل رمزگشایی نیست.")
                !imageUrl.isNullOrBlank() -> downloadImage(imageUrl)
                else -> throw IllegalStateException(
                    "سرویس پاسخ داده، اما فیلد تصویر قابل استفاده پیدا نشد. فیلدهای خروجی: ${imageItem.keys().asSequence().toList()}"
                )
            }

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bitmap ?: throw IllegalStateException(
                "داده برگشتی تصویر قابل نمایش نیست (فرمت/محتوای دریافتی ناشناخته است). " +
                    "اندازه فایل: ${bytes.size} بایت؛ امضای فایل: ${bytes.take(12).joinToString("") { "%02X".format(it) }}. " +
                    "مدل انتخابی: ${model.trim()}."
            )
        }
    }

    private fun findImageObject(root: JSONObject): JSONObject? {
        val arrays = listOf("data", "images", "output", "results")
        for (key in arrays) {
            val value = root.opt(key)
            when (value) {
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.optJSONObject(i)
                        if (item != null && hasImageField(item)) return item
                    }
                }
                is JSONObject -> {
                    if (hasImageField(value)) return value
                    val nested = findImageObject(value)
                    if (nested != null) return nested
                }
            }
        }
        if (hasImageField(root)) return root
        return null
    }

    private fun hasImageField(item: JSONObject): Boolean =
        listOf("b64_json", "image_base64", "base64", "image", "url", "image_url", "imageUrl")
            .any { item.opt(it) is String && item.optString(it).isNotBlank() }

    private fun firstString(item: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            item.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }

    private fun decodeBase64(value: String): ByteArray? {
        val normalized = value.trim()
            .replace(Regex("^data:image/[^;]+;base64,", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), "")
        return runCatching { Base64.decode(normalized, Base64.DEFAULT) }.getOrNull()
            ?: runCatching { Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull()
    }

    private fun downloadImage(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "image/png,image/*;q=0.9,*/*;q=0.5")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .get()
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("دریافت تصویر تولیدشده ناموفق بود (HTTP ${response.code}).")
            }
            val bytes = response.body?.bytes()
                ?: throw IllegalStateException("فایل تصویر خالی است.")
            if (bytes.isEmpty()) throw IllegalStateException("فایل تصویر خالی است.")
            bytes
        }
    }
}
