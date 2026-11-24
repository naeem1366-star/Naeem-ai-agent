package com.naeem.ai

import android.content.Context
import android.net.Uri
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

data class NetArzVideoJob(
    val id: String,
    val status: String,
    val progress: Int,
    val model: String,
    val estimatedUsd: String,
    val error: String?
)

data class NetArzMusicResult(
    val bytes: ByteArray,
    val format: String,
    val text: String,
    val model: String,
    val seconds: Double?,
    val requestId: String
)

data class NetArzAccount(
    val status: String,
    val balanceUsd: String,
    val tomanEstimate: Long,
    val spentUsd: String,
    val keyPrefix: String
)

class NetArzApiClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val key = apiKey.trim().removePrefix("Bearer ").trim()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun request(path: String): Request.Builder {
        require(root.startsWith("https://", true)) { "آدرس API باید با https:// شروع شود." }
        require(key.isNotBlank()) { "کلید API نت‌ارز در تنظیمات ثبت نشده است." }
        return Request.Builder().url(root + path)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            val json = JSONObject(body)
            val error = json.opt("error")
            if (error is JSONObject) error.optString("message").ifBlank { error.toString() }
            else json.optString("message").ifBlank { body.take(500) }
        }.getOrDefault(body.take(500))
        return "خطای API (HTTP $code): $detail"
    }

    suspend fun fetchModels(type: String): List<String> = withContext(Dispatchers.IO) {
        val url = root + "/models?type=" + type
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body))
            val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }.distinct()
        }
    }

    suspend fun account(): NetArzAccount = withContext(Dispatchers.IO) {
        http.newCall(request("/me").get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body))
            val json = JSONObject(body)
            val balance = json.optJSONObject("balance") ?: JSONObject()
            val keyInfo = json.optJSONObject("key") ?: JSONObject()
            NetArzAccount(
                status = json.optString("status", "unknown"),
                balanceUsd = balance.optString("usd_display", balance.optString("usd", "—")),
                tomanEstimate = balance.optLong("toman_estimate", 0L),
                spentUsd = keyInfo.optString("spent_usd", "—"),
                keyPrefix = keyInfo.optString("prefix", "")
            )
        }
    }

    suspend fun createVideo(
        model: String,
        prompt: String,
        seconds: Int,
        size: String,
        imageBase64: String? = null
    ): NetArzVideoJob = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "توضیح ویدیو را بنویس." }
        require(prompt.length <= 4000) { "توضیح ویدیو نباید بیشتر از ۴۰۰۰ نویسه باشد." }
        val payload = JSONObject()
            .put("model", model)
            .put("prompt", prompt.trim())
            .put("seconds", seconds.toString())
            .put("size", size)
        if (!imageBase64.isNullOrBlank()) payload.put("image", imageBase64)
        val req = request("/videos")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body))
            parseJob(JSONObject(body))
        }
    }

    suspend fun waitForVideo(
        jobId: String,
        onProgress: suspend (NetArzVideoJob) -> Unit
    ): ByteArray = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + 25L * 60L * 1000L
        while (System.currentTimeMillis() < deadline) {
            val responseJob = http.newCall(request("/videos/$jobId").get().build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body))
                parseJob(JSONObject(body))
            }
            onProgress(responseJob)
            when (responseJob.status) {
                "completed" -> {
                    val contentRequest = request("/videos/$jobId/content")
                        .header("Accept", "video/mp4, application/octet-stream, */*")
                        .get().build()
                    return@withContext http.newCall(contentRequest).execute().use { response ->
                        if (!response.isSuccessful) throw IllegalStateException("دریافت MP4 ناموفق بود (HTTP ${response.code}).")
                        val bytes = response.body?.bytes() ?: ByteArray(0)
                        require(bytes.isNotEmpty()) { "فایل ویدیوی نهایی خالی است." }
                        bytes
                    }
                }
                "failed", "cancelled" -> throw IllegalStateException(
                    responseJob.error ?: "ساخت ویدیو با وضعیت ${responseJob.status} متوقف شد. در صورت شکست، طبق مستندات هزینه باید برگشت داده شود."
                )
            }
            delay(5000)
        }
        throw IllegalStateException("پردازش بیش از ۲۵ دقیقه طول کشید. وضعیت کار را در پنل نت‌ارز بررسی کن.")
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

    suspend fun musicModels(): List<String> = fetchModels("music")

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
        val req = request("/audio/music").header("Content-Type", "application/json")
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
            NetArzMusicResult(
                bytes = bytes,
                format = format,
                text = json.optString("text", ""),
                model = json.optString("model", model),
                seconds = clip.optDouble("seconds").takeIf { clip.has("seconds") && it > 0.0 },
                requestId = json.optString("id", "")
            )
        }
    }

    companion object {
        suspend fun imageAsBase64(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("خواندن عکس انتخاب‌شده ناموفق بود.")
            require(bytes.size <= 15 * 1024 * 1024) { "حجم عکس بیشتر از ۱۵ مگابایت است." }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}
