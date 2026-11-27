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

data class EmaCloudMemory(
    val id: String,
    val type: String,
    val content: String,
    val confidence: Double,
    val sensitivity: String,
    val pinned: Boolean,
    val updatedAt: String
)

/**
 * Account-scoped memory sync. The device keeps its local memory for offline use;
 * this client syncs only explicit/user-approved memories through the authenticated gateway.
 */
class EmaMemoryClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun listMemories(gatewayUrl: String, accessToken: String): List<EmaCloudMemory> =
        withContext(Dispatchers.IO) {
            val root = gatewayUrl.trim().trimEnd('/')
            require(root.startsWith("https://") && accessToken.isNotBlank()) {
                "برای همگام‌سازی حافظه، اتصال امن و ورود به حساب لازم است."
            }
            val request = Request.Builder()
                .url("$root/v1/memory/items")
                .header("apikey", EmaSupabaseConfig.PUBLISHABLE_KEY)
                .header("Authorization", "Bearer $accessToken")
                .get().build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(memoryError(response.code, raw))
                val items = JSONObject(raw).optJSONArray("items") ?: JSONArray()
                (0 until items.length()).mapNotNull { index ->
                    val item = items.optJSONObject(index) ?: return@mapNotNull null
                    EmaCloudMemory(
                        id = item.optString("id"),
                        type = item.optString("memory_type", "note"),
                        content = item.optString("content"),
                        confidence = item.optDouble("confidence", 0.8),
                        sensitivity = item.optString("sensitivity", "normal"),
                        pinned = item.optBoolean("pinned", false),
                        updatedAt = item.optString("updated_at")
                    ).takeIf { it.content.isNotBlank() }
                }
            }
        }

    suspend fun saveMemory(
        gatewayUrl: String,
        accessToken: String,
        content: String,
        type: String = "note",
        source: String = "user_explicit",
        sensitivity: String = "normal"
    ): EmaCloudMemory = withContext(Dispatchers.IO) {
        val root = gatewayUrl.trim().trimEnd('/')
        val clean = content.trim()
        require(root.startsWith("https://") && accessToken.isNotBlank())
        require(clean.isNotBlank() && clean.length <= 4000)
        val payload = JSONObject()
            .put("content", clean)
            .put("memoryType", type)
            .put("source", source)
            .put("sensitivity", sensitivity)
        val request = Request.Builder()
            .url("$root/v1/memory/items")
            .header("apikey", EmaSupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(memoryError(response.code, raw))
            val item = JSONObject(raw).optJSONObject("item") ?: JSONObject(raw)
            EmaCloudMemory(
                id = item.optString("id"),
                type = item.optString("memory_type", type),
                content = item.optString("content", clean),
                confidence = item.optDouble("confidence", 0.8),
                sensitivity = item.optString("sensitivity", sensitivity),
                pinned = item.optBoolean("pinned", false),
                updatedAt = item.optString("updated_at")
            )
        }
    }

    suspend fun deleteMemory(gatewayUrl: String, accessToken: String, id: String) =
        withContext(Dispatchers.IO) {
            require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "شناسه حافظه معتبر نیست." }
            val root = gatewayUrl.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$root/v1/memory/items/$id")
                .header("apikey", EmaSupabaseConfig.PUBLISHABLE_KEY)
                .header("Authorization", "Bearer $accessToken")
                .delete().build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(memoryError(response.code, raw))
            }
        }

    private fun memoryError(code: Int, raw: String): String {
        val detail = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() } ?: JSONObject(raw).optString("message")
        }.getOrDefault("")
        return when (code) {
            401 -> "برای همگام‌سازی حافظه دوباره وارد حساب شو."
            404, 503 -> "حافظه ابری هنوز روی سرور فعال نشده؛ حافظه محلی همچنان در دسترس است."
            else -> detail.ifBlank { "همگام‌سازی حافظه ناموفق بود (HTTP $code)." }
        }
    }
}
