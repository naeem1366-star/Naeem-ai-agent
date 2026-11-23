package com.naeem.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private data class MarketQuote(
    val id: String,
    val name: String,
    val family: String,
    val price: Double,
    val unit: String,
    val change: Double?,
    val updated: String,
    val stale: Boolean
)

private val MarketInk = Color(0xFF0A0C10)
private val MarketPanel = Color(0xFF1B1F25)
private val MarketAccent = Color(0xFFD4D9E0)

@Composable
fun MarketsDashboard(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("naeem_market_cache", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var quotes by remember { mutableStateOf<List<MarketQuote>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var updatedAt by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("همه") }

    fun parsePayload(raw: String): List<MarketQuote> {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val nameObj = o.optJSONObject("name")
            val name = nameObj?.optString("fa").orEmpty().ifBlank { o.optString("name_fa", o.optString("id")) }
            val price = o.optDouble("price", Double.NaN)
            if (price.isNaN() || price <= 0.0) return@mapNotNull null
            MarketQuote(
                id = o.optString("id"),
                name = name,
                family = o.optString("family", "other"),
                price = price,
                unit = o.optString("unit"),
                change = if (o.isNull("change_pct")) null else o.optDouble("change_pct"),
                updated = o.optString("updated_utc", root.optString("generated_utc")),
                stale = o.optBoolean("stale", false)
            )
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = ""
            try {
                val result = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://nerkhara.com/api/v1/latest.json")
                        .header("Accept", "application/json")
                        .build()
                    OkHttpClient().newCall(request).execute().use { response ->
                        if (!response.isSuccessful) kotlin.error("پاسخ سرور: ${response.code}")
                        response.body?.string() ?: kotlin.error("پاسخ خالی از سرور")
                    }
                }
                val parsed = parsePayload(result)
                if (parsed.isEmpty()) kotlin.error("ساختار داده دریافتی قابل‌خواندن نیست.")
                quotes = parsed
                updatedAt = JSONObject(result).optString("generated_utc")
                prefs.edit().putString("payload", result).apply()
            } catch (e: Exception) {
                error = e.message ?: "دریافت قیمت‌ها ناموفق بود."
                if (quotes.isEmpty()) {
                    val cached = prefs.getString("payload", null)
                    if (cached != null) runCatching { quotes = parsePayload(cached); updatedAt = JSONObject(cached).optString("generated_utc") }
                }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val cached = prefs.getString("payload", null)
        if (cached != null) runCatching { quotes = parsePayload(cached); updatedAt = JSONObject(cached).optString("generated_utc") }
        refresh()
    }

    val categories = listOf("همه", "ارز", "طلا و فلزات", "رمزارز", "انرژی")
    val visible = quotes.filter { q ->
        when (selected) {
            "ارز" -> q.family.contains("currency") || q.id in listOf("usd", "eur", "gbp", "aed", "cny", "usdt")
            "طلا و فلزات" -> q.family.contains("gold") || q.family.contains("metal") || q.family.contains("coin") || q.id.contains("gold") || q.id.contains("silver") || q.id in listOf("xau", "xag", "gram18", "gram24", "ounce")
            "رمزارز" -> q.family.contains("crypto") || q.id in listOf("btc", "eth", "sol", "xrp", "bnb", "doge", "trx", "ltc", "dot", "ton")
            "انرژی" -> q.family.contains("commodity") || q.id.contains("oil") || q.id in listOf("brent", "wti", "natural-gas")
            else -> true
        }
    }

    fun formatted(value: Double): String = NumberFormat.getNumberInstance(Locale("fa", "IR")).format(value)
    fun unitLabel(unit: String): String = when (unit.uppercase()) {
        "IRT" -> "تومان"
        "USD" -> "دلار"
        "USDT" -> "تتر"
        else -> unit
    }

    Surface(Modifier.fillMaxSize(), color = MarketInk) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("نبض بازار", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("قیمت دارایی‌ها و بازارهای مالی", color = Color(0xFF9CA4B3), fontSize = 12.sp)
                }
                IconButton(onClick = { refresh() }, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MarketAccent, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, null, tint = MarketAccent)
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selected == category,
                        onClick = { selected = category },
                        label = { Text(category, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MarketAccent.copy(alpha = 0.2f),
                            selectedLabelColor = MarketAccent,
                            labelColor = Color(0xFFB8BFCC)
                        )
                    )
                }
            }

            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFFFA59D), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (updatedAt.isBlank()) "در انتظار دریافت قیمت‌ها" else "آخرین داده: ${updatedAt.replace("T", " ")} UTC", color = Color(0xFF8993A3), fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("به‌روزرسانی دستی", color = MarketAccent, fontSize = 10.sp)
            }

            if (quotes.isEmpty() && loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MarketAccent) }
            } else if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (quotes.isEmpty()) "برای دریافت قیمت‌ها اتصال اینترنت را بررسی کن." else "برای این گروه فعلاً داده‌ای موجود نیست.", color = Color(0xFFB8BFCC))
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(visible, key = { it.id }) { quote ->
                        Row(
                            Modifier.fillMaxWidth().background(MarketPanel, RoundedCornerShape(17.dp)).padding(horizontal = 15.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(quote.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${quote.id.uppercase()}  •  ${if (quote.stale) "داده قدیمی" else "به‌روز"}  •  ${quote.updated.replace("T", " ")} UTC", color = Color(0xFF8D97A8), fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${formatted(quote.price)} ${unitLabel(quote.unit)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                quote.change?.let {
                                    val positive = it >= 0
                                    Text(
                                        "${if (positive) "▲" else "▼"} ${formatted(kotlin.math.abs(it))}٪",
                                        color = if (positive) Color(0xFFD4D9E0) else Color(0xFFFF8C85),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text("منبع داده: NerkhAra  •  نرخ‌های بازار آزاد ایران و بازار جهانی. قیمت‌ها صرفاً جهت اطلاع‌اند.", color = Color(0xFF737E8E), fontSize = 10.sp, modifier = Modifier.padding(vertical = 8.dp).clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://nerkhara.com/en/"))) })
        }
    }
}
