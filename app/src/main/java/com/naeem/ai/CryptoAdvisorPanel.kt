package com.naeem.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private data class CryptoCandidate(
    val id: String,
    val name: String,
    val symbol: String,
    val price: Double,
    val change24h: Double,
    val change7d: Double,
    val marketCap: Double,
    val volume24h: Double,
    val score: Int,
    val note: String,
    val risk: String
)

private fun cryptoScore(change24h: Double, change7d: Double, marketCap: Double, volume: Double): Int {
    var score = 35.0
    score += when {
        change7d in 2.0..12.0 -> 22.0
        change7d > 12.0 -> 12.0
        change7d in 0.0..2.0 -> 12.0
        change7d < -12.0 -> -18.0
        else -> -8.0
    }
    score += when {
        change24h in 0.0..4.0 -> 14.0
        change24h in 4.0..8.0 -> 8.0
        change24h > 8.0 -> -8.0
        change24h < -8.0 -> -16.0
        else -> -4.0
    }
    val turnover = if (marketCap > 0.0) volume / marketCap else 0.0
    score += when {
        turnover >= 0.08 -> 16.0
        turnover >= 0.03 -> 10.0
        turnover >= 0.01 -> 4.0
        else -> -4.0
    }
    if (marketCap >= 1_000_000_000.0) score += 8.0
    if (abs(change24h) > 15.0 || abs(change7d) > 30.0) score -= 12.0
    return score.toInt().coerceIn(0, 100)
}

@Composable
fun CryptoAdvisorPanel(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var coins by remember { mutableStateOf<List<CryptoCandidate>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var updated by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            loading = true
            error = ""
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build()
                    val url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=60&page=1&sparkline=false&price_change_percentage=24h%2C7d"
                    val request = Request.Builder().url(url).header("Accept", "application/json").build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("دریافت داده بازار ناموفق بود (HTTP ${response.code}).")
                        response.body?.string() ?: error("پاسخ بازار خالی بود.")
                    }
                }
                val array = JSONArray(result)
                val parsed = (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val price = item.optDouble("current_price", Double.NaN)
                    val marketCap = item.optDouble("market_cap", 0.0)
                    val volume = item.optDouble("total_volume", 0.0)
                    val change24 = item.optDouble("price_change_percentage_24h", Double.NaN)
                    val change7 = item.optDouble("price_change_percentage_7d_in_currency", Double.NaN)
                    if (price.isNaN() || change24.isNaN() || change7.isNaN() || price <= 0.0) return@mapNotNull null
                    val score = cryptoScore(change24, change7, marketCap, volume)
                    val risk = when {
                        abs(change24) > 15.0 || abs(change7) > 30.0 -> "نوسان بسیار بالا"
                        marketCap < 1_000_000_000.0 -> "ریسک نقدشوندگی و اندازه بازار"
                        else -> "ریسک بازار؛ همچنان بالا"
                    }
                    val note = when {
                        score >= 75 -> "قدرت نسبی بهتر؛ برای بررسی بیشتر زیر نظر بگیر"
                        score >= 60 -> "نشانه‌های ترکیبی متوسط؛ منتظر تأیید روند بمان"
                        score >= 45 -> "سیگنال‌ها متناقض‌اند؛ عجله نکن"
                        else -> "فعلاً شرایط ضعیف‌تر؛ در اولویت پایین"
                    }
                    CryptoCandidate(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        symbol = item.optString("symbol").uppercase(),
                        price = price,
                        change24h = change24,
                        change7d = change7,
                        marketCap = marketCap,
                        volume24h = volume,
                        score = score,
                        note = note,
                        risk = risk
                    )
                }.sortedByDescending { it.score }.take(12)
                if (parsed.isEmpty()) error("داده کافی برای تحلیل دریافت نشد؛ کمی بعد دوباره تلاش کن.")
                coins = parsed
                updated = java.text.SimpleDateFormat("HH:mm  •  yyyy/MM/dd", Locale.getDefault()).format(java.util.Date())
            } catch (e: Exception) {
                error = e.message ?: "خطا در تحلیل بازار."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun money(value: Double): String = NumberFormat.getNumberInstance(Locale.US).format(value)
    fun compact(value: Double): String = when {
        value >= 1_000_000_000_000.0 -> "${"%.2f".format(Locale.US, value / 1_000_000_000_000.0)}T"
        value >= 1_000_000_000.0 -> "${"%.2f".format(Locale.US, value / 1_000_000_000.0)}B"
        value >= 1_000_000.0 -> "${"%.1f".format(Locale.US, value / 1_000_000.0)}M"
        else -> money(value)
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("رادار فرصت‌های رمزارز", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (updated.isBlank()) "بررسی داده‌های زنده بازار" else "آخرین بررسی: $updated", color = Color(0xFF8993A3), fontSize = 10.sp)
            }
            IconButton(onClick = { refresh() }, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(19.dp), color = SupraGlass.Ice, strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, null, tint = SupraGlass.Ice)
            }
        }
        Surface(
            color = Color(0xFF16283D),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x445C8AB8)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("روش رتبه‌بندی", color = SupraGlass.Ice, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text("ترکیب روند ۷روزه و ۲۴ساعته، حجم معاملات نسبت به ارزش بازار، اندازه بازار و شدت نوسان. امتیاز فقط ابزار غربالگری است؛ پیش‌بینی قطعی قیمت نیست.", color = Color(0xFFB8C4D3), fontSize = 10.sp, lineHeight = 16.sp)
            }
        }
        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFFFA59D), fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (coins.isEmpty() && loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SupraGlass.Ice)
            }
        } else if (coins.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("برای دریافت تحلیل، اتصال اینترنت را بررسی و دوباره تلاش کن.", color = Color(0xFFB8BFCC), fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(coins, key = { it.id }) { coin ->
                    Surface(
                        color = MarketPanel,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x223F6C98)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(coin.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(coin.symbol, color = Color(0xFF8D97A8), fontSize = 10.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$${money(coin.price)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("امتیاز ${coin.score}/100", color = if (coin.score >= 70) Color(0xFF9DD9C1) else Color(0xFFB9C8D9), fontSize = 10.sp)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricPill("۲۴ ساعت", coin.change24h)
                                MetricPill("۷ روز", coin.change7d)
                            }
                            Text(coin.note, color = Color(0xFFD5DFEA), fontSize = 11.sp)
                            Text("ارزش بازار: $${compact(coin.marketCap)}  •  حجم ۲۴ساعت: $${compact(coin.volume24h)}", color = Color(0xFF8993A3), fontSize = 9.sp)
                            Text("ریسک: ${coin.risk}", color = Color(0xFFFFC58C), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Text("منبع: CoinGecko API  •  رتبه‌بندی خودکار و آموزشی؛ پیش از هر تصمیم، نمودار و خبرها را جداگانه بررسی کن.", color = Color(0xFF737E8E), fontSize = 9.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun MetricPill(label: String, value: Double) {
    val positive = value >= 0
    Surface(color = if (positive) Color(0x223D8D72) else Color(0x22B75C58), shape = RoundedCornerShape(9.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$label: ${if (positive) "+" else ""}${"%.2f".format(Locale.US, value)}٪", color = if (positive) Color(0xFF9DD9C1) else Color(0xFFFFA59D), fontSize = 10.sp)
        }
    }
}
