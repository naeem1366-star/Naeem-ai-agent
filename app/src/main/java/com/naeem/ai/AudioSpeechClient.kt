package com.naeem.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Generates Persian speech through the configured OpenAI-compatible provider.
 * This avoids relying on a Persian voice pack installed on the Android device.
 */
class AudioSpeechClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Split long Persian answers into natural speech-sized parts. */
    suspend fun synthesizeChunks(baseUrl: String, apiKey: String, text: String): List<ByteArray> {
        val chunks = splitForSpeech(text)
        return chunks.map { part -> synthesize(baseUrl, apiKey, part) }
    }

    private fun splitForSpeech(text: String, maxChars: Int = 900): List<String> {
        val sentences = text.replace("\r\n", "\n")
            .split(Regex("(?<=[.!?؟؛])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                result.add(current.toString().trim())
                current.setLength(0)
            }
        }

        for (sentence in sentences) {
            if (sentence.length <= maxChars) {
                if (current.isNotEmpty() && current.length + sentence.length + 1 > maxChars) flush()
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            } else {
                flush()
                for (word in sentence.split(Regex("\\s+"))) {
                    if (current.isNotEmpty() && current.length + word.length + 1 > maxChars) flush()
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(word)
                }
                flush()
            }
        }
        flush()
        return result.ifEmpty { listOf(text.take(maxChars)) }
    }

    suspend fun synthesize(baseUrl: String, apiKey: String, text: String): ByteArray =
        withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim().let { value ->
                if (value.startsWith("Bearer ", ignoreCase = true)) value.substringAfter(' ').trim()
                else value
            }
            require(cleanKey.isNotBlank()) { "کلید API در تنظیمات وارد نشده است." }
            require(cleanKey.all { it.code in 0x21..0x7E }) {
                "مقدار فیلد کلید API معتبر نیست؛ فقط کلید اصلی انگلیسی را از پنل GapGPT کپی کن. متن فارسی، فاصله یا توضیحات را وارد نکن."
            }
            require(text.isNotBlank()) { "متنی برای تبدیل به صدا وجود ندارد." }

            var root = baseUrl.trim().trimEnd('/')
            require(root.startsWith("https://", ignoreCase = true)) {
                "نشانی سرویس باید با https:// شروع شود."
            }
            if (root.endsWith("/audio/speech", ignoreCase = true)) {
                root = root.removeSuffix("/audio/speech")
            }
            val endpoint = "$root/audio/speech"
            val payload = JSONObject()
                .put("model", "gpt-4o-mini-tts")
                .put("voice", "nova")
                .put("input", text.take(4096))
                .put("instructions", "Speak Persian (Farsi) in a warm, natural, conversational voice. Use clear pronunciation, gentle sentence-level pauses, varied intonation, and a comfortable moderate pace. Do not sound robotic or rush through the text.")
                .put("response_format", "mp3")

            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $cleanKey")
                .header("Accept", "audio/mpeg, application/octet-stream")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            http.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (!response.isSuccessful) {
                    val responseText = bytes.toString(Charsets.UTF_8)
                    val detail = runCatching {
                        val json = JSONObject(responseText)
                        val err = json.opt("error")
                        if (err is JSONObject) err.optString("message").ifBlank { err.toString() }
                        else if (err is String) err
                        else json.optString("message").ifBlank { responseText }
                    }.getOrDefault(responseText).take(700)
                    val hint = when (response.code) {
                        401 -> "\nکلید API را در تنظیمات برنامه بررسی کن."
                        403 -> "\nدسترسی یا اعتبار حساب سرویس را بررسی کن."
                        404 -> "\nممکن است مدل gpt-4o-mini-tts در سرویس فعال نباشد."
                        429 -> "\nمحدودیت درخواست یا اعتبار API را بررسی کن."
                        else -> ""
                    }
                    throw IllegalStateException("خطای ساخت صدای فارسی (HTTP ${response.code}): $detail$hint")
                }
                require(bytes.isNotEmpty()) { "سرویس صوتی فایل خالی برگرداند." }
                val contentType = response.header("Content-Type").orEmpty()
                require(!contentType.contains("application/json", ignoreCase = true)) {
                    "سرویس به‌جای فایل صوتی، پاسخ JSON فرستاد: ${bytes.toString(Charsets.UTF_8).take(400)}"
                }
                bytes
            }
        }
}
