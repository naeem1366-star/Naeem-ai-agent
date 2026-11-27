package com.naeem.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Paid media requests go through the authenticated EMA Edge Function.
 * NetArz credentials are never sent to or stored by the Android client.
 */
class NetArzMediaGatewayClient(
    private val gatewayUrl: String,
    private val accessToken: String
) {
    private val root = gatewayUrl.trim().trimEnd('/')
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun request(path: String): Request.Builder {
        require(root.startsWith("https://", true)) { "درگاه امن EMA تنظیم نشده است." }
        require(accessToken.isNotBlank()) { "برای استفاده از ابزار پولی ابتدا وارد حساب ایما شو." }
        return Request.Builder().url(root + path)
            .header("apikey", EmaSupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            error?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").ifBlank { body.take(400) }
        }.getOrDefault(body.take(400))
        return "درخواست رسانه‌ای ناموفق بود (HTTP $code): $detail"
    }

    private fun parseJob(json: JSONObject) = NetArzVideoJob(
        id = json.optString("id"),
        status = json.optString("status", "unknown"),
        progress = json.optInt("progress", 0).coerceIn(0, 100),
        model = json.optString("model"),
        estimatedUsd = json.optString("usd", ""),
        error = json.optJSONObject("error")?.optString("message")
            ?: json.optString("error").takeIf { it.isNotBlank() && it != "null" }
    )

    suspend fun videoModelCatalog(): List<NetArzVideoModel> = withContext(Dispatchers.IO) {
        http.newCall(request("/v1/media/models?type=video").get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body))
            val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val caps = item.optJSONObject("capabilities") ?: JSONObject()
                fun strings(key: String) = caps.optJSONArray(key)?.let { array ->
                    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
                NetArzVideoModel(
                    id = id,
                    name = item.optString("name").ifBlank { id.substringAfterLast('/') },
                    description = item.optString("description"),
                    seconds = strings("seconds").mapNotNull { it.toIntOrNull() },
                    sizes = strings("sizes"),
                    imageInput = caps.optBoolean("image_input", false),
                    videoInput = caps.optBoolean("video_input", id.contains("higgsfield", true)),
                    audio = caps.optBoolean("audio", false)
                )
            }
        }
    }

    suspend fun musicModels(): List<String> = withContext(Dispatchers.IO) {
        http.newCall(request("/v1/media/models?type=music").get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body))
            val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }.distinct()
        }
    }

    suspend fun generateMusic(
        model: String,
        prompt: String,
        seconds: Double? = null,
        temperature: Double? = null,
        seed: Long? = null
    ): NetArzMusicResult = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "توضیح موسیقی را بنویس." }
        require(prompt.length <= 4000) { "توضیح موسیقی نباید بیشتر از ۴۰۰۰ نویسه باشد." }
        val payload = JSONObject().put("model", model).put("prompt", prompt.trim())
        if (seconds != null) payload.put("seconds", seconds)
        if (temperature != null) payload.put("temperature", temperature)
        if (seed != null) payload.put("seed", seed)
        val req = request("/v1/media/music").header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        http.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body))
            val json = JSONObject(body)
            val clip = json.optJSONArray("data")?.optJSONObject(0)
                ?: throw IllegalStateException("پاسخ سرویس فایل موسیقی نداشت.")
            val encoded = clip.optString("b64_json").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("فایل موسیقی در پاسخ API خالی بود.")
            val format = clip.optString("format", "mp3").lowercase().filter { it.isLetterOrDigit() }.ifBlank { "mp3" }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(bytes.isNotEmpty()) { "فایل موسیقی دریافتی خالی است." }
            NetArzMusicResult(bytes, format, json.optString("text", ""), json.optString("model", model),
                clip.optDouble("seconds").takeIf { clip.has("seconds") && it > 0.0 }, json.optString("id", ""))
        }
    }

    suspend fun createVideo(
        model: String,
        prompt: String,
        seconds: Int,
        size: String,
        imageBase64: String? = null,
        videoBase64: String? = null
    ): NetArzVideoJob = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "توضیح ویدیو را بنویس." }
        require(prompt.length <= 4000) { "توضیح ویدیو نباید بیشتر از ۴۰۰۰ نویسه باشد." }
        val payload = JSONObject().put("model", model).put("prompt", prompt.trim())
            .put("seconds", seconds.toString()).put("size", size)
        if (!imageBase64.isNullOrBlank()) payload.put("image", imageBase64)
        if (!videoBase64.isNullOrBlank()) payload.put("video", videoBase64)
        val req = request("/v1/media/videos").header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        http.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body))
            parseJob(JSONObject(body))
        }
    }

    suspend fun waitForVideo(jobId: String, onProgress: suspend (NetArzVideoJob) -> Unit): ByteArray =
        withContext(Dispatchers.IO) {
            require(jobId.matches(Regex("[A-Za-z0-9_-]{1,160}"))) { "شناسه درخواست ویدیو معتبر نیست." }
            val deadline = System.currentTimeMillis() + 25L * 60L * 1000L
            while (System.currentTimeMillis() < deadline) {
                val job = http.newCall(request("/v1/media/videos/$jobId").get().build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body))
                    parseJob(JSONObject(body))
                }
                onProgress(job)
                when (job.status) {
                    "completed" -> return@withContext http.newCall(
                        request("/v1/media/videos/$jobId/content")
                            .header("Accept", "video/mp4, application/octet-stream, */*").get().build()
                    ).execute().use { response ->
                        if (!response.isSuccessful) throw IllegalStateException("دریافت MP4 ناموفق بود (HTTP ${response.code}).")
                        val bytes = response.body?.bytes() ?: ByteArray(0)
                        require(bytes.isNotEmpty()) { "فایل ویدیوی نهایی خالی است." }
                        bytes
                    }
                    "failed", "cancelled" -> throw IllegalStateException(job.error ?: "ساخت ویدیو با وضعیت ${job.status} متوقف شد.")
                }
                delay(5000)
            }
            throw IllegalStateException("پردازش بیش از ۲۵ دقیقه طول کشید. وضعیت درخواست را از پشتیبانی پیگیری کن.")
        }
}
