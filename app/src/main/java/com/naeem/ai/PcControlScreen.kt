package com.naeem.ai

import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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

@Composable
fun PcControlScreen(onClose: () -> Unit) {
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8765") }
    var pin by remember { mutableStateOf("") }
    var fingerprint by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("برای شروع، کامپیوتر و گوشی را به یک شبکه وصل کن.") }
    var discovering by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<PcBridgeDevice>>(emptyList()) }
    var screen by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var textToType by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val client = remember {
        PcBridgeClient(
            onStatus = {
                status = it
                if (it.startsWith("ارتباط قطع شد") || it.startsWith("اتصال بسته شد")) connected = false
            },
            onScreen = { bytes ->
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null) screen = decoded.asImageBitmap()
            }
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
                    OutlinedButton(onClick = { client.disconnect(); connected = false; screen = null }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(7.dp))
                        Text("قطع اتصال")
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
                                    if (connected) {
                                        client.sendMouseMove(point.x / size.width, point.y / size.height)
                                        client.click()
                                    }
                                }
                            }
                            .pointerInput(frame, connected) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    if (connected) client.sendMouseMove(change.position.x / size.width, change.position.y / size.height)
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
                        OutlinedButton(onClick = { client.key(key) }, enabled = connected, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                            Text(label, color = accent, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = textToType,
                    onValueChange = { textToType = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("متن برای تایپ در کامپیوتر") },
                    enabled = connected,
                    minLines = 2
                )
                Button(onClick = { client.typeText(textToType); textToType = "" }, enabled = connected && textToType.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("ارسال متن به کامپیوتر")
                }
                Text("باز کردن نرم‌افزار", color = accent, style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("blender" to "Blender", "freecad" to "FreeCAD", "krita" to "Krita").forEach { (app, label) ->
                        OutlinedButton(onClick = { client.launchApp(app) }, enabled = connected, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                            Text(label, color = accent, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Text(status, color = if (connected) Color(0xFF9AD6B0) else muted, style = MaterialTheme.typography.bodySmall)
        Text("امنیت: تصویر فقط پس از جفت‌سازی دریافت می‌شود. کنترل ماوس و صفحه‌کلید باید جداگانه از پنجره ویندوز فعال شود. این نسخه فقط برای شبکه محلی است.", color = muted, style = MaterialTheme.typography.labelSmall)
    }
}
