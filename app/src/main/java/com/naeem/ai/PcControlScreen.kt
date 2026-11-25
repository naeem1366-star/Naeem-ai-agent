package com.naeem.ai

import android.util.Base64
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONObject
import kotlinx.coroutines.delay
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class PcPlannedAction(
    val type: String,
    val app: String = "",
    val key: String = "",
    val text: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val delta: Int = 0,
    val waitMs: Long = 0L
)

@Composable
fun PcControlScreen(onClose: () -> Unit, baseUrl: String, model: String, apiKey: String) {
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8765") }
    var pin by remember { mutableStateOf("") }
    var fingerprint by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("برای شروع، کامپیوتر و گوشی را به یک شبکه وصل کن.") }
    var discovering by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var remoteControlAllowed by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<PcBridgeDevice>>(emptyList()) }
    var screen by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var textToType by remember { mutableStateOf("") }
    var taskPrompt by remember { mutableStateOf("") }
    var planSummary by remember { mutableStateOf("") }
    var plannedActions by remember { mutableStateOf<List<PcPlannedAction>>(emptyList()) }
    var planning by remember { mutableStateOf(false) }
    var runningPlan by remember { mutableStateOf(false) }
    var latestScreenBase64 by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val aiClient = remember { ChatClient() }
    val context = LocalContext.current
    val qrScanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val raw = result.data?.getStringExtra("SCAN_RESULT")
        if (result.resultCode == Activity.RESULT_OK && !raw.isNullOrBlank()) {
            runCatching {
                val payload = JSONObject(raw)
                require(payload.optString("type") == "naeem_ai_pc_pair") { "این کد مربوط به اتصال کامپیوتر نیست." }
                val scannedIp = payload.getString("ip")
                val scannedPort = payload.optInt("port", 8765)
                val scannedPin = payload.getString("pin")
                val scannedFingerprint = payload.getString("fingerprint")
                require(scannedIp.isNotBlank() && scannedPort in 1..65535 && scannedPin.length == 6 && scannedFingerprint.filter(Char::isLetterOrDigit).length == 64) {
                    "اطلاعات کد اتصال کامل یا معتبر نیست."
                }
                ip = scannedIp
                port = scannedPort.toString()
                pin = scannedPin
                fingerprint = scannedFingerprint
                status = "کد کامپیوتر خوانده شد؛ اطلاعات اتصال آماده است. حالا اتصال را بزن."
            }.onFailure { status = it.message ?: "خواندن کد ناموفق بود." }
        }
    }
    val client = remember {
        PcBridgeClient(
            onStatus = {
                status = it
                if (it.startsWith("ارتباط قطع شد") || it.startsWith("اتصال بسته شد")) connected = false
            },
            onScreen = { bytes ->
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null) {
                    screen = decoded.asImageBitmap()
                    latestScreenBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            },
            onControlPermission = { remoteControlAllowed = it }
        )
    }
    DisposableEffect(client) { onDispose { client.dispose() } }

    val background = Color(0xFF101827)
    val panel = Color(0xFF202A3A)
    val accent = Color(0xFFD4D9E0)
    val muted = Color(0xFFB9C5D5)

    Column(
        Modifier.fillMaxSize().background(background).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Computer, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("کنترل کامپیوتر", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("اتصال مستقیم و رمزگذاری‌شده در شبکه محلی", color = muted, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onClose) { Text("بستن", color = accent) }
        }

        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("برنامه ویندوز", color = accent, style = MaterialTheme.typography.titleMedium)
                Text("این فایل را از همین‌جا دریافت کن، به کامپیوتر ویندوزی منتقل و نصب کن؛ سپس برنامه دسکتاپ را باز نگه دار.", color = muted, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        try {
                            val request = DownloadManager.Request(
                                Uri.parse("https://github.com/naeem1366-star/Naeem-ai-agent/releases/download/desktop-bridge-alpha1/NaeemAI-DesktopBridge-Setup-alpha1.exe")
                            )
                                .setTitle("نصب‌کننده ویندوز Naeem AI")
                                .setDescription("در حال دریافت برنامه اتصال کامپیوتر")
                                .setMimeType("application/vnd.microsoft.portable-executable")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setAllowedOverMetered(true)
                                .setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    "NaeemAI-DesktopBridge-Setup-alpha1.exe"
                                )
                            val manager = context.getSystemService(DownloadManager::class.java)
                                ?: throw IllegalStateException("مدیریت دانلود در این گوشی در دسترس نیست.")
                            manager.enqueue(request)
                            status = "دانلود نصب‌کننده شروع شد؛ فایل در پوشه Downloads گوشی قرار می‌گیرد."
                        } catch (e: Exception) {
                            status = e.message ?: "شروع دانلود ناموفق بود."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("دانلود نصب‌کننده ویندوز")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("۱. پیدا کردن کامپیوتر", color = accent, style = MaterialTheme.typography.titleMedium)
                Text("برنامه دسکتاپ را در ویندوز باز نگه دار. گوشی و کامپیوتر باید به یک مودم یا شبکه محلی وصل باشند.", color = muted, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        discovering = true
                        devices = emptyList()
                        scope.launch {
                            try {
                                devices = client.discover()
                                status = if (devices.isEmpty()) "کامپیوتری پیدا نشد. فایروال ویندوز و یکسان بودن شبکه را بررسی کن؛ می‌توانی IP را دستی وارد کنی."
                                else "کامپیوتر پیدا شد؛ آن را انتخاب کن."
                            } catch (e: Exception) { status = e.message ?: "جست‌وجو ناموفق بود." }
                            discovering = false
                        }
                    },
                    enabled = !discovering && !connecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (discovering) "در حال جست‌وجو…" else "جست‌وجوی کامپیوتر")
                }
                devices.forEach { device ->
                    OutlinedButton(
                        onClick = {
                            ip = device.ip
                            port = device.port.toString()
                            fingerprint = device.fingerprint
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(device.name + "  •  " + device.ip, color = accent)
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("۲. جفت‌سازی امن", color = accent, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = {
                        val activity = context as? Activity
                        if (activity == null) {
                            status = "دوربین در این صفحه در دسترس نیست."
                        } else {
                            qrScanner.launch(
                                IntentIntegrator(activity)
                                    .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                                    .setPrompt("کد اتصال نمایش‌داده‌شده روی کامپیوتر را اسکن کن")
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false)
                                    .createScanIntent()
                            )
                        }
                    },
                    enabled = !connecting && !connected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Link, null)
                    Spacer(Modifier.width(7.dp))
                    Text("اسکن کد اتصال با دوربین")
                }
                Text("روی کامپیوتر کد QR را باز کن و با دوربین گوشی اسکن کن؛ نشانی، کد شش‌رقمی و اثر انگشت امنیتی خودکار وارد می‌شوند.", color = muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = ip, onValueChange = { ip = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("IP کامپیوتر") }, placeholder = { Text("مثلاً 192.168.1.20") })
                OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("درگاه") })
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("کد شش‌رقمی روی کامپیوتر") })
                OutlinedTextField(value = fingerprint, onValueChange = { fingerprint = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("اثر انگشت امنیتی کامپیوتر") }, placeholder = { Text("از پنجره دسکتاپ کپی یا از جست‌وجو دریافت کن") })
                Button(
                    onClick = {
                        connecting = true
                        scope.launch {
                            try {
                                client.connect(ip.trim(), port.toInt(), pin, fingerprint)
                                connected = true
                            } catch (e: Exception) {
                                connected = false
                                status = e.message ?: "اتصال ناموفق بود."
                            } finally { connecting = false }
                        }
                    },
                    enabled = !connecting && !connected && ip.isNotBlank() && port.isNotBlank() && pin.length == 6 && fingerprint.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Link, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (connecting) "در حال اتصال…" else "اتصال به کامپیوتر")
                }
                if (connected) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try { remoteControlAllowed = client.refreshControlPermission(ip.trim(), port.toInt()) }
                                catch (e: Exception) { status = e.message ?: "بررسی مجوز کنترل ناموفق بود." }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("به‌روزرسانی اجازه کنترل از ویندوز") }
                    OutlinedButton(onClick = { client.disconnect(); connected = false; remoteControlAllowed = false; screen = null }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(7.dp))
                        Text("قطع اتصال")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("دستور هوشمند به کامپیوتر", color = accent, style = MaterialTheme.typography.titleMedium)
                Text("درخواستت را فارسی بنویس. دستیار با دیدن آخرین تصویر صفحه، مراحل پیشنهادی را می‌سازد؛ هیچ مرحله‌ای بدون تأیید تو اجرا نمی‌شود.", color = muted, style = MaterialTheme.typography.bodySmall)
                Text("نکته حریم خصوصی: با زدن دکمه تحلیل، تصویر صفحه و متن درخواست به سرویس هوش مصنوعی تنظیم‌شده در برنامه ارسال می‌شود. پیش از تحلیل، پنجره‌ها و اطلاعات خصوصی را ببند.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = taskPrompt,
                    onValueChange = { taskPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("چه کاری روی کامپیوتر انجام بدهم؟") },
                    placeholder = { Text("مثلاً Blender را باز کن و یک کره در مرکز صحنه بساز…") },
                    enabled = connected && remoteControlAllowed && !planning && !runningPlan
                )
                Button(
                    onClick = {
                        planning = true
                        plannedActions = emptyList()
                        planSummary = ""
                        scope.launch {
                            try {
                                val systemPrompt = """
تو دستیار کنترل کامپیوتر هستی. باید فقط یک JSON معتبر و بدون Markdown برگردانی.
ساختار دقیق:
{"summary":"خلاصه فارسی کار","actions":[{"type":"launch_app","app":"blender"},{"type":"wait","ms":8000},{"type":"key","key":"SHIFT+F4"},{"type":"type_text","text":"..."},{"type":"key","key":"ENTER"}]}
فقط این actionها مجازند:
- launch_app با app یکی از blender, freecad, krita, gimp, inkscape, 3dsmax, photoshop
- wait با ms بین 200 و 15000
- key با میانبر صفحه‌کلید مانند CTRL+S یا SHIFT+F5 یا ENTER
- type_text با متن حداکثر 2000 نویسه
- mouse_move با x و y بین 0 و 1
- mouse_click
- mouse_right_click
- mouse_scroll با delta برابر 120 برای بالا و -120 برای پایین
حداکثر 15 action تولید کن. برای Blender می‌توانی با SHIFT+F4 به Python Console بروی، فرمان bpy را اجرا کنی و با SHIFT+F5 به نمای سه‌بعدی برگردی. در 3ds Max می‌توانی با F11 پنجره MAXScript Listener را باز کنی و فرمان‌های MaxScript را وارد کنی. برای Photoshop فقط از ابزارها و میانبرهای معمول رابط کاربری استفاده کن. برای FreeCAD از رابط و فرمان‌های شناخته‌شده آن استفاده کن. از دستورهای سیستم‌عامل، PowerShell، cmd، حذف فایل، نصب برنامه، خاموش‌کردن، تغییر رجیستری یا دسترسی به اطلاعات خصوصی استفاده نکن. اگر تصویر صفحه نشان می‌دهد برنامه آماده نیست، ابتدا وضعیت را بررسی کن و مراحل لازم را پیشنهاد بده. اگر برای انجام کار اطلاعات کافی نداری، summary را با سؤال روشن‌کننده شروع کن و actions را خالی بگذار.
درخواست کاربر: ${taskPrompt.trim()}
""".trimIndent()
                                val answer = aiClient.send(
                                    baseUrl, model, apiKey, systemPrompt, latestScreenBase64
                                )
                                val jsonStart = answer.indexOf('{')
                                val jsonEnd = answer.lastIndexOf('}')
                                require(jsonStart >= 0 && jsonEnd > jsonStart) {
                                    "پاسخ مدل در قالب JSON معتبر نبود؛ دوباره تحلیل کن."
                                }
                                val parsed = JSONObject(answer.substring(jsonStart, jsonEnd + 1))
                                val actionArray = parsed.optJSONArray("actions")
                                    ?: throw IllegalStateException("فهرست مراحل در پاسخ مدل وجود ندارد.")
                                require(actionArray.length() <= 15) { "تعداد مراحل بیش از حد مجاز است." }
                                val safeActions = buildList {
                                    for (i in 0 until actionArray.length()) {
                                        val item = actionArray.optJSONObject(i) ?: continue
                                        when (item.optString("type")) {
                                            "launch_app" -> {
                                                val app = item.optString("app").lowercase()
                                                require(app in setOf("blender", "freecad", "krita", "gimp", "inkscape", "3dsmax", "photoshop")) { "برنامه پیشنهادی مجاز نیست." }
                                                add(PcPlannedAction(type = "launch_app", app = app))
                                            }
                                            "wait" -> {
                                                val ms = item.optLong("ms", 1000L).coerceIn(200L, 15000L)
                                                add(PcPlannedAction(type = "wait", waitMs = ms))
                                            }
                                            "key" -> {
                                                val key = item.optString("key").take(40)
                                                require(key.isNotBlank() && key.matches(Regex("[A-Za-z0-9+_ ]+"))) { "میانبر صفحه‌کلید نامعتبر است." }
                                                add(PcPlannedAction(type = "key", key = key))
                                            }
                                            "type_text" -> {
                                                val text = item.optString("text").take(2000)
                                                require(text.isNotBlank()) { "متن فرمان خالی است." }
                                                add(PcPlannedAction(type = "type_text", text = text))
                                            }
                                            "mouse_move" -> {
                                                val x = item.optDouble("x", -1.0)
                                                val y = item.optDouble("y", -1.0)
                                                require(x in 0.0..1.0 && y in 0.0..1.0) { "مختصات ماوس نامعتبر است." }
                                                add(PcPlannedAction(type = "mouse_move", x = x.toFloat(), y = y.toFloat()))
                                            }
                                            "mouse_click" -> add(PcPlannedAction(type = "mouse_click"))
                                            "mouse_right_click" -> add(PcPlannedAction(type = "mouse_right_click"))
                                            "mouse_scroll" -> add(PcPlannedAction(type = "mouse_scroll", delta = item.optInt("delta", 120).coerceIn(-1200, 1200)))
                                            else -> throw IllegalStateException("مدل یک مرحله خارج از فهرست مجاز پیشنهاد داده است.")
                                        }
                                    }
                                }
                                planSummary = parsed.optString("summary", "مراحل پیشنهادی آماده شد.")
                                plannedActions = safeActions
                                status = if (safeActions.isEmpty()) "دستیار برای این درخواست هنوز فرمان اجرایی مطمئنی پیشنهاد نکرده است."
                                    else "برنامه آماده بررسی است؛ برای اجرا باید آن را تأیید کنی."
                            } catch (e: Exception) {
                                status = e.message ?: "ساخت برنامه اجرایی ناموفق بود."
                            } finally { planning = false }
                        }
                    },
                    enabled = connected && !planning && !runningPlan && taskPrompt.isNotBlank() && apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (planning) "در حال بررسی تصویر و ساخت مراحل…" else "تحلیل درخواست با هوش مصنوعی")
                }
                if (planSummary.isNotBlank()) {
                    Text(planSummary, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    plannedActions.forEachIndexed { index, action ->
                        val detail = when (action.type) {
                            "launch_app" -> "باز کردن ${action.app}"
                            "wait" -> "مکث ${action.waitMs / 1000.0} ثانیه"
                            "key" -> "کلیدها: ${action.key}"
                            "type_text" -> "متن کامل فرمان برای تایپ:\n${action.text}"
                            "mouse_move" -> "حرکت ماوس به ${action.x}, ${action.y}"
                            "mouse_right_click" -> "کلیک راست ماوس"
                            "mouse_scroll" -> "اسکرول ماوس: ${action.delta}"
                            else -> "کلیک ماوس"
                        }
                        Text("${index + 1}. $detail", color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (plannedActions.isNotEmpty()) {
                        Text("فرمان‌ها پس از تأیید تو اجرا می‌شوند. متن‌هایی که در برنامه مقصد تایپ می‌شوند را پیش از اجرا مرور کن.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.labelSmall)
                        Button(
                            onClick = {
                                runningPlan = true
                                scope.launch {
                                    try {
                                        plannedActions.forEachIndexed { index, action ->
                                            status = "در حال اجرای مرحله ${index + 1} از ${plannedActions.size}…"
                                            when (action.type) {
                                                "launch_app" -> client.launchApp(action.app)
                                                "wait" -> delay(action.waitMs)
                                                "key" -> client.key(action.key)
                                                "type_text" -> client.typeText(action.text)
                                                "mouse_move" -> client.sendMouseMove(action.x, action.y)
                                                "mouse_click" -> client.click()
                                                "mouse_right_click" -> client.rightClick()
                                                "mouse_scroll" -> client.scroll(action.delta)
                                            }
                                            if (action.type !in setOf("wait")) delay(350)
                                        }
                                        status = "مراحل اجرا شدند؛ تصویر زنده را بررسی کن و اگر لازم بود نتیجه را دوباره تحلیل کن."
                                    } catch (e: Exception) {
                                        status = e.message ?: "اجرای مراحل متوقف شد."
                                    } finally { runningPlan = false }
                                }
                            },
                            enabled = connected && remoteControlAllowed && !runningPlan,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (runningPlan) "در حال اجرا…" else "تأیید و اجرای مراحل")
                        }
                        OutlinedButton(
                            onClick = { plannedActions = emptyList(); planSummary = "" },
                            enabled = !runningPlan,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("لغو برنامه پیشنهادی") }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("۳. تصویر زنده کامپیوتر", color = accent, style = MaterialTheme.typography.titleMedium)
                if (screen != null) {
                    val frame = screen!!
                    Image(
                        bitmap = frame,
                        contentDescription = "تصویر زنده صفحه کامپیوتر؛ لمس برای حرکت ماوس و کلیک",
                        modifier = Modifier.fillMaxWidth().aspectRatio(frame.width.toFloat() / frame.height.toFloat())
                            .pointerInput(frame, connected) {
                                detectTapGestures { point ->
                                    if (connected && remoteControlAllowed) {
                                        client.sendMouseMove(point.x / size.width, point.y / size.height)
                                        client.click()
                                    }
                                }
                            }
                            .pointerInput(frame, connected) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    if (connected && remoteControlAllowed) client.sendMouseMove(change.position.x / size.width, change.position.y / size.height)
                                }
                            },
                        contentScale = ContentScale.FillBounds
                    )
                    Text("روی تصویر لمس کن تا کلیک شود؛ انگشت را بکش تا نشانگر حرکت کند.", color = muted, style = MaterialTheme.typography.labelSmall)
                } else {
                    Box(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFF151E2B), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("پس از اتصال، تصویر صفحه اینجا نمایش داده می‌شود.", color = muted)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CTRL+S" to "ذخیره", "CTRL+Z" to "بازگشت", "ENTER" to "Enter", "ESC" to "Esc").forEach { (key, label) ->
                        OutlinedButton(onClick = { client.key(key) }, enabled = connected && remoteControlAllowed, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                            Text(label, color = accent, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { client.rightClick() }, enabled = connected && remoteControlAllowed, modifier = Modifier.weight(1f)) {
                        Text("کلیک راست", color = accent, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { client.scroll(120) }, enabled = connected && remoteControlAllowed, modifier = Modifier.weight(1f)) {
                        Text("اسکرول بالا", color = accent, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { client.scroll(-120) }, enabled = connected && remoteControlAllowed, modifier = Modifier.weight(1f)) {
                        Text("اسکرول پایین", color = accent, style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedTextField(
                    value = textToType,
                    onValueChange = { textToType = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("متن برای تایپ در کامپیوتر") },
                    enabled = connected && remoteControlAllowed,
                    minLines = 2
                )
                Button(onClick = { client.typeText(textToType); textToType = "" }, enabled = connected && remoteControlAllowed && textToType.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("ارسال متن به کامپیوتر")
                }
                Text("باز کردن نرم‌افزار", color = accent, style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "blender" to "Blender",
                        "freecad" to "FreeCAD",
                        "3dsmax" to "3ds Max",
                        "photoshop" to "Photoshop",
                        "krita" to "Krita"
                    ).forEach { (app, label) ->
                        OutlinedButton(onClick = { client.launchApp(app) }, enabled = connected && remoteControlAllowed, contentPadding = PaddingValues(horizontal = 9.dp)) {
                            Text(label, color = accent, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Text(status, color = if (connected) Color(0xFF9AD6B0) else muted, style = MaterialTheme.typography.bodySmall)
        Text(if (connected && !remoteControlAllowed) "تصویر دریافت می‌شود، اما اجازه کنترل در ویندوز خاموش است. آن را روشن کن و وضعیت را به‌روزرسانی کن." else "امنیت: تصویر پس از جفت‌سازی دریافت می‌شود. کنترل از ویندوز به‌صورت جداگانه فعال می‌شود. این نسخه فقط برای شبکه محلی است.", color = muted, style = MaterialTheme.typography.labelSmall)
    }
}
