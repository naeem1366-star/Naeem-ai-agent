package com.naeem.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
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
    val stale: Boolean,
    val source: String = "NerkhAra"
)

private val MarketInk = Color.Transparent
private val MarketPanel = SupraGlass.Glass
private val MarketAccent = SupraGlass.Ice


private fun fetchPublicMarketQuotes(): List<MarketQuote> {
    val client = OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(9)).build()
    fun get(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "EMA-Market-Research/1.0").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Empty response")
        }
    }
    val result = mutableListOf<MarketQuote>()
    runCatching {
        val arr = org.json.JSONArray(get("https://data-api.binance.vision/api/v3/ticker/24hr"))
        val wanted = setOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "DOGEUSDT")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val symbol = o.optString("symbol")
            if (symbol !in wanted) continue
            result += MarketQuote(symbol, symbol.removeSuffix("USDT"), "crypto",
                o.optDouble("lastPrice"), "USDT", o.optDouble("priceChangePercent"),
                "Public API • Binance", false, "Binance public API")
        }
    }
    val yahoo = listOf(
        Triple("^GSPC", "S&P 500", "global_stock"),
        Triple("^IXIC", "NASDAQ", "global_stock"),
        Triple("^DJI", "Dow Jones", "global_stock"),
        Triple("^N225", "Nikkei 225", "global_stock"),
        Triple("GC=F", "Gold futures", "commodity"),
        Triple("SI=F", "Silver futures", "commodity"),
        Triple("CL=F", "WTI crude oil", "commodity"),
        Triple("BZ=F", "Brent crude oil", "commodity"),
        Triple("HG=F", "Copper futures", "commodity")
    )
    yahoo.forEach { (symbol, name, family) ->
        runCatching {
            val encoded = java.net.URLEncoder.encode(symbol, "UTF-8")
            val root = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=5d&interval=15m"))
            val meta = root.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            val price = meta.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN()) return@runCatching
            val previous = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", 0.0))
            val change = if (previous > 0) ((price - previous) / previous) * 100 else 0.0
            val unit = meta.optString("currency", "USD")
            result += MarketQuote(symbol, name, family, price, unit, change,
                "Yahoo public chart", false, "Yahoo Finance public chart")
        }
    }
    runCatching {
        val root = JSONObject(get("https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR,GBP,CHF,JPY"))
        val rates = root.getJSONObject("rates")
        for (currency in listOf("EUR", "GBP", "CHF", "JPY")) {
            val value = rates.optDouble(currency, Double.NaN)
            if (!value.isNaN()) result += MarketQuote("USD/$currency", "USD → $currency", "currency",
                value, currency, null, root.optString("date"), false, "Frankfurter / ECB reference")
        }
    }
    runCatching {
        val root = JSONObject(get("https://call1.tgju.org/ajax.json"))
        val current = root.optJSONObject("current") ?: error("TGJU feed shape changed")
        val iranItems = listOf(
            Triple("price_dollar_rl", "دلار آزاد", "currency"),
            Triple("geram18", "طلای ۱۸ عیار", "gold"),
            Triple("sekee", "سکه امامی", "gold"),
            Triple("ons", "اونس جهانی طلا", "gold")
        )
        iranItems.forEach { (key, name, family) ->
            val item = current.optJSONObject(key) ?: return@forEach
            val raw = item.optString("p").replace(",", "").toDoubleOrNull() ?: return@forEach
            val isGlobal = key == "ons"
            val price = if (isGlobal) raw else raw / 10.0
            val unit = if (isGlobal) "USD/oz" else "تومان"
            result += MarketQuote(key, name, family, price, unit, null,
                "TGJU public snapshot", false, "TGJU public feed")
        }
    }
    runCatching {
        val url = "https://cdn.tsetmc.com/api/MarketData/GetMarketOverview/0"
        val root = JSONObject(get(url))
        val data = root.optJSONArray("marketOverview")
        val o = data?.optJSONObject(0) ?: root.optJSONObject("marketOverview")
        if (o != null && o.optDouble("indexLastValue", 0.0) > 0) {
            result += MarketQuote("TEDPIX", "شاخص کل بورس تهران", "iran_stock",
                o.optDouble("indexLastValue"), "واحد شاخص", o.optDouble("indexChange"),
                "TSETMC public feed", false, "TSETMC public endpoint")
        }
    }
    return result
}


private fun fetchMarketHistory(quote: MarketQuote, range: String): List<Double> {
    val client = OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(12)).build()
    val (yahooRange, yahooInterval, binanceInterval, limit) = when (range) {
        "1D" -> listOf("1d", "5m", "15m", 96)
        "1W" -> listOf("5d", "15m", "1h", 168)
        else -> listOf("1mo", "1d", "4h", 180)
    }
    fun get(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "EMA-Market-Research/1.0").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("پاسخ خالی")
        }
    }
    return when {
        quote.family == "crypto" -> {
            val url = "https://data-api.binance.vision/api/v3/klines?symbol=${quote.id}&interval=$binanceInterval&limit=$limit"
            val rows = org.json.JSONArray(get(url))
            (0 until rows.length()).mapNotNull { i ->
                rows.optJSONArray(i)?.optString(4)?.toDoubleOrNull()
            }.filter { it > 0.0 }
        }
        quote.family == "global_stock" || quote.family == "commodity" -> {
            val symbol = java.net.URLEncoder.encode(quote.id, "UTF-8")
            val root = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?range=$yahooRange&interval=$yahooInterval"))
            val result = root.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val closes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
            (0 until closes.length()).mapNotNull { i ->
                val value = if (closes.isNull(i)) null else closes.optDouble(i, Double.NaN)
                value?.takeIf { it.isFinite() && it > 0.0 }
            }
        }
        else -> emptyList()
    }
}

@Composable
private fun MarketHistoryChart(values: List<Double>, positive: Boolean, modifier: Modifier = Modifier) {
    val lineColor = if (positive) Color(0xFF63D6A7) else Color(0xFFFF8C85)
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val low = values.minOrNull() ?: return@Canvas
        val high = values.maxOrNull() ?: return@Canvas
        val span = (high - low).takeIf { it > 0.0 } ?: (high * 0.01).coerceAtLeast(1.0)
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(Color.White.copy(alpha = 0.09f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1).toFloat()
            val y = size.height - (((value - low) / span).toFloat() * size.height * 0.82f + size.height * 0.08f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

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
    var searchText by remember { mutableStateOf("") }
    var selectedAsset by remember { mutableStateOf<MarketQuote?>(null) }
    var historyRange by remember { mutableStateOf("1W") }
    var historyValues by remember { mutableStateOf<List<Double>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf("") }

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
                stale = o.optBoolean("stale", false),
                source = "NerkhAra"
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
                val publicQuotes = withContext(Dispatchers.IO) { fetchPublicMarketQuotes() }
                quotes = (parsed + publicQuotes).distinctBy { it.id }
                updatedAt = JSONObject(result).optString("generated_utc")
                prefs.edit().putString("payload", result).apply()
            } catch (e: Exception) {
                val publicOnly = withContext(Dispatchers.IO) { runCatching { fetchPublicMarketQuotes() }.getOrDefault(emptyList()) }
                if (publicOnly.isNotEmpty()) quotes = publicOnly
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

    LaunchedEffect(selectedAsset?.id, historyRange) {
        val asset = selectedAsset ?: return@LaunchedEffect
        historyLoading = true
        historyError = ""
        historyValues = emptyList()
        val values = withContext(Dispatchers.IO) { runCatching { fetchMarketHistory(asset, historyRange) } }
        values.onSuccess { historyValues = it; if (it.size < 2) historyError = "داده تاریخی معتبر برای این دارایی موجود نیست." }
            .onFailure { historyError = "دریافت نمودار ناموفق بود؛ منبع داده موقتاً در دسترس نیست." }
        historyLoading = false
    }

    val categories = listOf("همه", "رمزارز", "دلار و ارز", "طلا", "بورس ایران", "بورس جهانی", "کالاها", "پیشنهاد خرید")
    val visible = quotes.filter { q ->
        when (selected) {
            "دلار و ارز" -> q.family.contains("currency") || q.id in listOf("usd", "eur", "gbp", "aed", "cny", "usdt")
            "طلا" -> q.family.contains("gold") || q.family.contains("coin") || q.id.contains("gold") || q.id in listOf("xau", "gram18", "gram24", "ounce", "geram18", "sekee", "ons")
            "رمزارز" -> q.family.contains("crypto") || q.id in listOf("btc", "eth", "sol", "xrp", "bnb", "doge", "trx", "ltc", "dot", "ton")
            "کالاها" -> q.family.contains("commodity") || q.family.contains("metal") || q.id.contains("oil") || q.id in listOf("brent", "wti", "natural-gas", "xag", "silver")
            "بورس ایران" -> q.family == "iran_stock"
            "بورس جهانی" -> q.family == "global_stock"
            else -> true
        }
    }.filter { q ->
        searchText.isBlank() || q.name.contains(searchText, ignoreCase = true) || q.id.contains(searchText, ignoreCase = true)
    }

    val observedChanges = visible.mapNotNull { it.change }
    val positiveBreadth = observedChanges.count { it > 0 }
    val negativeBreadth = observedChanges.count { it < 0 }
    val averageMove = if (observedChanges.isNotEmpty()) observedChanges.average() else null
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

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                singleLine = true,
                placeholder = { Text("جست‌وجوی دارایی، نماد یا بازار", color = Color(0xFF8993A3), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MarketAccent) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MarketAccent, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedContainerColor = MarketPanel, unfocusedContainerColor = MarketPanel
                )
            )
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFFFA59D), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (updatedAt.isBlank()) "در انتظار دریافت قیمت‌ها" else "آخرین داده: ${updatedAt.replace("T", " ")} UTC", color = Color(0xFF8993A3), fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("به‌روزرسانی دستی", color = MarketAccent, fontSize = 10.sp)
            }

            if (selected != "پیشنهاد خرید" && observedChanges.isNotEmpty()) {
                val mood = when {
                    positiveBreadth > negativeBreadth -> "تمایل مثبت در داده‌های قابل‌مقایسه"
                    negativeBreadth > positiveBreadth -> "تمایل منفی در داده‌های قابل‌مقایسه"
                    else -> "بازار ترکیبی و بدون برتری روشن"
                }
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        .background(MarketPanel, RoundedCornerShape(16.dp)).padding(14.dp)
                ) {
                    Text("تحلیلگر نبض بازار", color = MarketAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(mood, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                    Text(
                        "مثبت: $positiveBreadth  •  منفی: $negativeBreadth  •  میانگین تغییر روزانه/دوره‌ای: ${averageMove?.let { formatted(it) } ?: "—"}٪",
                        color = Color(0xFFADB6C5), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)
                    )
                    Text("این جمع‌بندی فقط از تغییرات گزارش‌شده منابع عمومی ساخته شده و سیگنال خرید یا پیش‌بینی قطعی نیست.",
                        color = Color(0xFF7F8A9B), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }

            if (selectedAsset != null && selected != "پیشنهاد خرید") {
                val asset = selectedAsset!!
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        .background(MarketPanel, RoundedCornerShape(20.dp)).padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(asset.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${asset.id.uppercase()} · ${asset.source}", color = Color(0xFF8993A3), fontSize = 10.sp)
                        }
                        IconButton(onClick = { selectedAsset = null }) { Icon(Icons.Default.Close, "بستن نمودار", tint = Color(0xFF9CA4B3)) }
                    }
                    Text("${formatted(asset.price)} ${unitLabel(asset.unit)}", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        listOf("1D", "1W", "1M").forEach { range ->
                            FilterChip(
                                selected = historyRange == range,
                                onClick = { historyRange = range },
                                label = { Text(range, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MarketAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = MarketAccent,
                                    labelColor = Color(0xFFB8BFCC)
                                )
                            )
                        }
                    }
                    when {
                        historyLoading -> Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MarketAccent, strokeWidth = 2.dp)
                        }
                        historyValues.size >= 2 -> {
                            val positive = historyValues.last() >= historyValues.first()
                            MarketHistoryChart(historyValues, positive, Modifier.fillMaxWidth().height(132.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("شروع: ${formatted(historyValues.first())}", color = Color(0xFF8993A3), fontSize = 10.sp)
                                Text("پایان: ${formatted(historyValues.last())}", color = Color(0xFF8993A3), fontSize = 10.sp)
                            }
                        }
                        else -> Text(historyError.ifBlank { "نمودار تاریخی برای این بازار هنوز پشتیبانی نمی‌شود." }, color = Color(0xFFB8BFCC), fontSize = 11.sp)
                    }
                    Text("نمودار بر اساس داده تاریخی منبع اعلام‌شده است؛ پیش‌بینی قیمت نیست.", color = Color(0xFF737E8E), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            if (selected == "پیشنهاد خرید") {
                CryptoAdvisorPanel(Modifier.weight(1f))
            } else if (quotes.isEmpty() && loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MarketAccent) }
            } else if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(when (selected) { "بورس ایران", "بورس جهانی" -> "اتصال منبع داده معتبر این بازار هنوز تکمیل نشده است؛ فعلاً داده‌ای نمایش داده نمی‌شود."; else -> if (quotes.isEmpty()) "برای دریافت قیمت‌ها اتصال اینترنت را بررسی کن." else "برای این گروه فعلاً داده‌ای موجود نیست." }, color = Color(0xFFB8BFCC))
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(visible, key = { it.id }) { quote ->
                        Row(
                            Modifier.fillMaxWidth().background(MarketPanel, RoundedCornerShape(17.dp))
                                .clickable { selectedAsset = quote }.padding(horizontal = 15.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(quote.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${quote.id.uppercase()}  •  ${quote.source}  •  ${if (quote.stale) "داده قدیمی" else "دریافت‌شده"}", color = Color(0xFF8D97A8), fontSize = 10.sp)
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

            Text("منابع عمومی بدون کلید: NerkhAra، Binance، Yahoo Finance، Frankfurter و TSETMC. نرخ مرجع ارز، نرخ آزاد ایران نیست. قیمت‌ها صرفاً جهت اطلاع‌اند.", color = Color(0xFF737E8E), fontSize = 10.sp, modifier = Modifier.padding(vertical = 8.dp).clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://nerkhara.com/en/"))) })
        }
    }
}
