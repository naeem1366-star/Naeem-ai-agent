package com.naeem.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebFinding(val title: String, val url: String, val snippet: String)

class DeepSearchClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun search(
        query: String,
        limit: Int = 8,
        gatewayUrl: String? = null,
        accessToken: String? = null
    ): List<WebFinding> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "عبارت جست‌وجو خالی است." }
        val serverResults = if (!gatewayUrl.isNullOrBlank() && !accessToken.isNullOrBlank()) {
            runCatching { searchViaGateway(query, limit, gatewayUrl, accessToken) }.getOrNull()
        } else null
        val results = serverResults?.takeIf { it.isNotEmpty() } ?: searchDuckDuckGo(query, limit)
        enrich(results)
    }

    private fun searchViaGateway(
        query: String,
        limit: Int,
        gatewayUrl: String,
        accessToken: String
    ): List<WebFinding> {
        val root = gatewayUrl.trim().trimEnd('/')
        val body = JSONObject().put("query", query.trim()).put("count", limit.coerceIn(1, 10))
        val request = Request.Builder()
            .url("$root/v1/search/web")
            .header("apikey", EmaSupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $accessToken")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Server web search unavailable")
            val items = JSONObject(response.body?.string().orEmpty()).optJSONArray("results")
                ?: return@use emptyList()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title").trim()
                val url = item.optString("url").trim()
                val snippet = item.optString("snippet").trim()
                if (title.isBlank() || !url.startsWith("https://")) null
                else WebFinding(title, url, snippet)
            }.distinctBy { it.url }.take(limit)
        }
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<WebFinding> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept-Language", "fa,en;q=0.8")
            .get()
            .build()

        val results = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("جست‌وجوی وب ناموفق بود (HTTP ${response.code}).")
            val doc = Jsoup.parse(response.body?.string().orEmpty())
            doc.select("a.result__a").mapNotNull { link ->
                val title = link.text().trim()
                val href = link.absUrl("href").ifBlank { link.attr("href") }
                val parent = link.parents().firstOrNull { it.select(".result__snippet").isNotEmpty() }
                val snippet = parent?.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
                if (title.isBlank() || href.isBlank()) null else WebFinding(title, href, snippet)
            }.distinctBy { it.url }.take(limit)
        }
        if (results.isEmpty()) {
            throw IllegalStateException("نتیجه‌ای از موتور جست‌وجو دریافت نشد؛ ممکن است دسترسی وب یا صفحه جست‌وجو محدود شده باشد.")
        }
        return results
    }

    private suspend fun enrich(results: List<WebFinding>): List<WebFinding> = coroutineScope {
        results.take(6).map { finding ->
            async(Dispatchers.IO) {
                val pageText = runCatching { fetchPage(finding.url) }.getOrNull()
                if (pageText.isNullOrBlank()) finding else finding.copy(snippet = pageText)
            }
        }.awaitAll()
    }

    private fun fetchPage(url: String): String {
        if (!url.startsWith("https://")) return ""
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .get().build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) "" else {
                val doc = Jsoup.parse(response.body?.string().orEmpty())
                doc.select("script,style,nav,footer,header,aside,form,noscript").remove()
                val main = doc.selectFirst("article, main, [role=main]") ?: doc.body()
                main?.text()?.replace(Regex("\\s+"), " ")?.take(2200).orEmpty()
            }
        }
    }
}
