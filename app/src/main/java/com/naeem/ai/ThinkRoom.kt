package com.naeem.ai

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject


private data class GoogleFinding(val title: String, val snippet: String, val url: String)

private object GoogleResearchClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun search(topic: String): List<GoogleFinding> = withContext(Dispatchers.IO) {
        val url = "https://www.google.com/search?q=" + Uri.encode(topic) + "&num=8&hl=fa"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.7")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("گوگل پاسخ مناسب نداد: HTTP " + response.code)
            val doc = Jsoup.parse(response.body?.string().orEmpty())
            doc.select("a:has(h3)").mapNotNull { link ->
                val heading = link.selectFirst("h3") ?: return@mapNotNull null
                val href = link.attr("href").takeIf { it.startsWith("http") } ?: return@mapNotNull null
                if (href.contains("google.com/search")) return@mapNotNull null
                val snippet = link.parent()?.parent()?.text()?.takeIf { it.length > heading.text().length } ?: ""
                GoogleFinding(heading.text(), snippet.take(700), href)
            }.distinctBy { it.url }.take(8)
        }
    }
}

private val ThinkBg = Color(0xFF0B0D11)
private val ThinkCard = Color(0xFF171A20)
private val ThinkLine = Color(0xFF292E38)
private val ThinkText = Color(0xFFE5E8ED)
private val ThinkMuted = Color(0xFF9AA2AE)

private data class ThinkProject(
    val id: Long,
    val title: String,
    val brief: String,
    val notes: String = "",
    val tasks: List<String> = emptyList()
)

private class ThinkRoomStore(context: Context) {
    private val prefs = context.getSharedPreferences("think_room_v1", Context.MODE_PRIVATE)
    fun load(): List<ThinkProject> = runCatching {
        val array = JSONArray(prefs.getString("projects", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val tasks = o.optJSONArray("tasks") ?: JSONArray()
            ThinkProject(
                o.optLong("id"), o.optString("title"), o.optString("brief"),
                o.optString("notes"),
                (0 until tasks.length()).mapNotNull { tasks.optString(it).takeIf(String::isNotBlank) }
            )
        }
    }.getOrDefault(emptyList())

    fun save(projects: List<ThinkProject>) {
        val array = JSONArray()
        projects.forEach { p ->
            val tasks = JSONArray()
            p.tasks.forEach(tasks::put)
            array.put(JSONObject().put("id", p.id).put("title", p.title)
                .put("brief", p.brief).put("notes", p.notes).put("tasks", tasks))
        }
        prefs.edit().putString("projects", array.toString()).apply()
    }
}

@Composable
fun ThinkRoomScreen(
    onClose: () -> Unit,
    baseUrl: String,
    model: String,
    apiKey: String
) {
    val context = LocalContext.current
    val store = remember { ThinkRoomStore(context) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var role by remember { mutableStateOf("هم‌فکر") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var webQuery by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf(store.load()) }
    var selectedProject by remember { mutableStateOf<Long?>(null) }
    var showNewProject by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newBrief by remember { mutableStateOf("") }
    var newTask by remember { mutableStateOf("") }

    fun persist(updated: List<ThinkProject>) {
        projects = updated
        store.save(updated)
    }

    Column(Modifier.fillMaxSize().background(ThinkBg).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = ThinkText) }
            Column(Modifier.weight(1f)) {
                Text("اتاق فکر", color = ThinkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("از ایده تا اجرا", color = ThinkMuted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.AutoAwesome, null, tint = ThinkMuted)
        }
        Row(Modifier.fillMaxWidth().background(ThinkCard, RoundedCornerShape(14.dp)).padding(4.dp)) {
            listOf("میز تحلیل", "پروژه‌ها").forEachIndexed { index, label ->
                Box(Modifier.weight(1f).background(if (tab == index) Color(0xFF303641) else Color.Transparent, RoundedCornerShape(11.dp))
                    .clickable { tab = index }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (tab == index) ThinkText else ThinkMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            0 -> {
                Text("از چند زاویه به موضوعت نگاه کن", color = ThinkText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("هم‌فکر", "استراتژیست", "طراح", "منتقد").forEach { item ->
                        FilterChip(selected = role == item, onClick = { role = item }, label = { Text(item) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (answer.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = ThinkCard), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("پاسخ اتاق فکر · $role", color = ThinkMuted, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(answer, color = ThinkText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = ThinkCard), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Lightbulb, null, tint = ThinkMuted, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("ایده‌ات را مطرح کن", color = ThinkText, fontWeight = FontWeight.SemiBold)
                            Text("می‌توانی از اتاق فکر بخواهی ایده‌ات را نقد کند، راه‌حل بدهد یا به یک نقشه اجرایی تبدیل کند.", color = ThinkMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF9B9B), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("موضوع یا ایده‌ات را بنویس…") }, minLines = 2, maxLines = 4,
                    shape = RoundedCornerShape(15.dp), colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThinkText, unfocusedTextColor = ThinkText,
                        focusedBorderColor = ThinkMuted, unfocusedBorderColor = ThinkLine,
                        focusedPlaceholderColor = ThinkMuted, unfocusedPlaceholderColor = ThinkMuted
                    )
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val prompt = question.trim()
                        if (prompt.isNotBlank() && !busy) {
                            busy = true; error = ""; answer = ""
                            question = ""
                            scope.launch {
                                try {
                                    val instruction = when (role) {
                                        "استراتژیست" -> "در نقش استراتژیست دقیق و واقع‌گرا پاسخ بده؛ بازار، منابع، ریسک‌ها و مسیر اجرا را بررسی کن."
                                        "طراح" -> "در نقش طراح خلاق فکر کن؛ چند راه‌حل متفاوت، تجربه کاربر و جزئیات اجرایی پیشنهاد بده."
                                        "منتقد" -> "در نقش منتقد سخت‌گیر اما منصف پاسخ بده؛ فرض‌های ضعیف، تناقض‌ها، ریسک‌ها و راه اصلاح را روشن کن."
                                        else -> "هم‌فکر عمیق و صریح باش؛ سؤال‌های لازم را مشخص کن، مزایا و معایب را بسنج و پیشنهاد عملی بده."
                                    }
                                    answer = ChatClient().send(baseUrl, model, apiKey,
                                        "تو اتاق فکر یک اپلیکیشن هستی. فقط فارسی روان بنویس. $instruction پاسخ را ساختارمند، کاربردی و بدون ادعای اطلاعات بررسی‌نشده ارائه کن. اگر داده به‌روز لازم است، صریح بگو باید در جست‌وجوی وب بررسی شود.\n\nموضوع کاربر:\n$prompt")
                                } catch (e: Exception) { error = e.message ?: "پاسخی دریافت نشد." }
                                finally { busy = false }
                            }
                        }
                    },
                    enabled = !busy && question.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDDE2EA), contentColor = Color(0xFF171A20))
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(if (busy) "در حال فکر کردن…" else "شروع هم‌فکری")
                }
            }
            1 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("پروژه‌های من", color = ThinkText, fontWeight = FontWeight.SemiBold)
                        Text("ایده‌ها، یادداشت‌ها و قدم‌های بعدی", color = ThinkMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = { showNewProject = true }) { Icon(Icons.Default.Add, null); Text("پروژه") }
                }
                Spacer(Modifier.height(10.dp))
                val active = projects.firstOrNull { it.id == selectedProject }
                if (active != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedProject = null }) { Icon(Icons.Default.ArrowBack, null, tint = ThinkText) }
                        Text(active.title, color = ThinkText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { persist(projects.filterNot { it.id == active.id }); selectedProject = null }) {
                            Icon(Icons.Default.DeleteOutline, null, tint = ThinkMuted)
                        }
                    }
                    OutlinedTextField(value = active.brief, onValueChange = { v -> persist(projects.map { if (it.id == active.id) it.copy(brief = v) else it }) },
                        label = { Text("شرح پروژه") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = active.notes, onValueChange = { v -> persist(projects.map { if (it.id == active.id) it.copy(notes = v) else it }) },
                        label = { Text("یادداشت‌ها و تصمیم‌ها") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                    Spacer(Modifier.height(10.dp))
                    Text("قدم‌های اجرایی", color = ThinkText, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newTask, onValueChange = { newTask = it }, modifier = Modifier.weight(1f), placeholder = { Text("قدم بعدی…") }, singleLine = true)
                        IconButton(onClick = {
                            if (newTask.isNotBlank()) { persist(projects.map { if (it.id == active.id) it.copy(tasks = it.tasks + newTask.trim()) else it }); newTask = "" }
                        }) { Icon(Icons.Default.AddCircle, null, tint = ThinkText) }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(active.tasks) { task ->
                            Row(Modifier.fillMaxWidth().background(ThinkCard, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(task, color = ThinkText, modifier = Modifier.weight(1f))
                                IconButton(onClick = { persist(projects.map { if (it.id == active.id) it.copy(tasks = it.tasks - task) else it }) }) {
                                    Icon(Icons.Default.Close, null, tint = ThinkMuted)
                                }
                            }
                        }
                    }
                } else if (projects.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, null, tint = ThinkMuted, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("هنوز پروژه‌ای نساختی", color = ThinkText)
                            Text("اولین ایده‌ات را به یک پروژه تبدیل کن.", color = ThinkMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(projects) { p ->
                            Card(Modifier.fillMaxWidth().clickable { selectedProject = p.id }, colors = CardDefaults.cardColors(containerColor = ThinkCard), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(p.title, color = ThinkText, fontWeight = FontWeight.SemiBold)
                                    if (p.brief.isNotBlank()) Text(p.brief, color = ThinkMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                    Text("${p.tasks.size} قدم اجرایی", color = ThinkMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                Text("میز تحلیل چندجانبه", color = ThinkText, fontWeight = FontWeight.SemiBold)
                Text("موضوعت را مطرح کن؛ اتاق فکر از چند زاویه بررسی می‌کند و برای شواهد و اطلاعات تازه، گوگل را جست‌وجو می‌کند.", color = ThinkMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("چه موضوعی را بررسی کنیم؟") }, minLines = 2, maxLines = 4)
                Spacer(Modifier.height(10.dp))
                listOf("استراتژیست" to "هدف، بازار، منابع و امکان‌پذیری", "طراح" to "راه‌حل‌های خلاقانه و تجربه متفاوت", "منتقد" to "نقاط ضعف، ریسک‌ها و فرض‌های نادرست", "پژوهشگر" to "شواهد و اطلاعات موردنیاز برای راستی‌آزمایی").forEach { (name, desc) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircleOutline, null, tint = ThinkMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column { Text(name, color = ThinkText); Text(desc, color = ThinkMuted, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    val topic = searchQuery.trim()
                    if (topic.isNotBlank() && !busy) {
                        busy = true; error = ""; answer = ""; searchQuery = ""
                        scope.launch {
                            try {
                                val findings = GoogleResearchClient.search(topic)
                                val sources = findings.joinToString("\n\n") { "- ${it.title}\n${it.snippet}\nمنبع: ${it.url}" }
                                val promptText = "تو میز تحلیل چندجانبه اتاق فکر هستی. فقط فارسی پاسخ بده. موضوع را از پنج دیدگاه بررسی کن: ۱) استراتژیست: هدف، بازار، منابع و امکان‌پذیری ۲) طراح: راه‌حل‌های خلاقانه و فرصت‌ها ۳) منتقد: فرض‌های نادرست، ریسک‌ها و نقاط کور ۴) پژوهشگر: ارزیابی شواهد و کیفیت منابع وب ۵) جمع‌بندی بی‌طرفانه و پیشنهاد قدم بعدی. اطلاعات زیر از جست‌وجوی گوگل به دست آمده‌اند؛ آن‌ها را شواهد اولیه بدان، نه حقیقت قطعی. ادعاها را با منبع مربوط مشخص کن و اگر منابع اختلاف دارند، اختلاف را توضیح بده. اگر نتیجه‌ای موجود نیست، صادقانه بگو.\nموضوع: " + topic + "\n\nنتایج جست‌وجوی گوگل:\n" + sources
                                answer = ChatClient().send(baseUrl, model, apiKey, promptText)
                            } catch (e: Exception) { error = e.message ?: "تحلیل انجام نشد. اگر گوگل کپچا نشان داد، اتصال یا دسترسی شبکه را بررسی کن." }
                            finally { busy = false }
                        }
                    }
                }, enabled = !busy && searchQuery.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp)); Text(if (busy) "در حال بررسی چندجانبه…" else "تحلیل چندجانبه موضوع")
                }
                if (answer.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = ThinkCard)) { Column(Modifier.padding(14.dp)) { Text("گزارش میز تحلیل", color = ThinkMuted, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); Text(answer, color = ThinkText) } }
                }
            }
        }
    }

    if (showNewProject) {
        AlertDialog(
            onDismissRequest = { showNewProject = false },
            title = { Text("پروژه جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("نام پروژه") }, singleLine = true)
                    OutlinedTextField(value = newBrief, onValueChange = { newBrief = it }, label = { Text("هدف یا توضیح کوتاه") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank()) {
                        persist(listOf(ThinkProject(System.currentTimeMillis(), newTitle.trim(), newBrief.trim())) + projects)
                        newTitle = ""; newBrief = ""; showNewProject = false
                    }
                }) { Text("ساخت پروژه") }
            },
            dismissButton = { TextButton(onClick = { showNewProject = false }) { Text("انصراف") } }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GoogleSearchWebView(query: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                webViewClient = WebViewClient()
            }
        },
        update = { view ->
            val target = "https://www.google.com/search?q=" + Uri.encode(query)
            if (view.tag != target) {
                view.tag = target
                view.loadUrl(target)
            }
        }
    )
}
