package com.naeem.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
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

    suspend fun search(query: String, limit: Int = 8): List<WebFinding> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "عبارت جست‌وجو خالی است." }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept-Language", "fa,en;q=0.8")
            .get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("جست‌وجوی وب ناموفق بود (HTTP ${response.code}).")
            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            val results = doc.select("a.result__a").mapNotNull { link ->
                val title = link.text().trim()
                val href = link.absUrl("href").ifBlank { link.attr("href") }
                val parent = link.parents().firstOrNull { it.select(".result__snippet").isNotEmpty() }
                val snippet = parent?.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
                if (title.isBlank() || href.isBlank()) null else WebFinding(title, href, snippet)
            }.distinctBy { it.url }.take(limit)
            if (results.isEmpty()) throw IllegalStateException("نتیجه‌ای از موتور جست‌وجو دریافت نشد؛ ممکن است دسترسی وب یا صفحه جست‌وجو محدود شده باشد.")
            coroutineScope {
                results.take(6).map { finding ->
                    async(Dispatchers.IO) {
                        val pageText = runCatching { fetchPage(finding.url) }.getOrNull()
                        if (pageText.isNullOrBlank()) finding else finding.copy(snippet = pageText)
                    }
                }.awaitAll()
            }
        }
    }

    private fun fetchPage(url: String): String {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return ""
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            doc.select("script,style,nav,footer,header,aside,form,noscript").remove()
            val main = doc.selectFirst("article, main, [role=main]") ?: doc.body()
            main?.text()?.replace(Regex("\\s+"), " ")?.take(2200).orEmpty()
        }
    }
}
