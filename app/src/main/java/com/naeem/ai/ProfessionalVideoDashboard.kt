package com.naeem.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProfessionalVideoDashboard(
    onClose: () -> Unit,
    baseUrl: String,
    apiKey: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember(baseUrl, apiKey) { NetArzApiClient(baseUrl, apiKey) }
    val defaults = listOf("sora-2", "sora-2-pro", "veo-3.1", "veo-3.1-fast", "veo-3.1-lite")
    var models by remember { mutableStateOf(defaults) }
    var selectedModel by remember { mutableStateOf("veo-3.1-fast") }
    var prompt by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(8) }
    var resolution by remember { mutableStateOf("720p") }
    var ratio by remember { mutableStateOf("9:16") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imagePreview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var loadingModels by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("برای شروع، مدل و تنظیمات خروجی را انتخاب کن.") }
    var error by remember { mutableStateOf("") }
    var confirmGeneration by remember { mutableStateOf(false) }
    var pendingVideo by remember { mutableStateOf<ByteArray?>(null) }
    var estimatedCost by remember { mutableStateOf("") }
    var accountBalance by remember { mutableStateOf("") }
    var showAccount by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            imageUri = uri
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch {
                runCatching {
                    val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                        ?: throw IllegalStateException("خواندن عکس ناموفق بود.")
                    imagePreview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    imageBase64 = NetArzApiClient.imageAsBase64(context, uri)
                }.onFailure { error = it.message ?: "خطای خواندن عکس" }
            }
        }
    }
    val saveVideo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val bytes = pendingVideo
        if (uri != null && bytes != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                            ?: throw IllegalStateException("محل ذخیره فایل باز نشد.")
                    }
                }.onSuccess { status = "ویدیوی MP4 با موفقیت ذخیره شد." }
                    .onFailure { error = it.message ?: "ذخیره فایل ناموفق بود." }
                pendingVideo = null
            }
        }
    }

    LaunchedEffect(baseUrl) {
        loadingModels = true
        runCatching { api.fetchModels("video") }
            .onSuccess { found -> if (found.isNotEmpty()) { models = found; if (selectedModel !in found) selectedModel = found.first() } }
            .onFailure { status = "فهرست زنده مدل‌ها دریافت نشد؛ مدل‌های پیش‌فرض نمایش داده شده‌اند. ${it.message}" }
        loadingModels = false
    }

    val bg = Color(0xFF0B0E13)
    val panel = Color(0xFF171D27)
    val muted = Color(0xFF9DA8B7)
    val silver = Color(0xFFDCE3ED)

    Column(
        Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !generating) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = silver) }
            Column(Modifier.weight(1f)) {
                Text("ویدیوساز حرفه‌ای", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("اتصال مستقیم به API نت‌ارز", color = muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFD6C08A), modifier = Modifier.size(27.dp))
        }

        Surface(color = Color(0xFF17271F), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("این بخش پولی است", color = Color(0xFFBCE4C5), fontWeight = FontWeight.Bold)
                Text("درخواست‌های تولید از اعتبار API نت‌ارز مصرف می‌کنند. ویدیوساز رایگان Supra در بخش جداگانه باقی می‌ماند و از این اعتبار استفاده نمی‌کند.", color = Color(0xFFD2DBD4), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { api.account() }.onSuccess {
                                accountBalance = "${it.balanceUsd}  •  ${NumberFormat.getNumberInstance(Locale("fa", "IR")).format(it.tomanEstimate)} تومان"
                                showAccount = true
                            }.onFailure { error = it.message ?: "دریافت موجودی ناموفق بود." }
                        }
                    }) { Text("بررسی موجودی و اعتبار") }
                    Spacer(Modifier.weight(1f))
                    if (loadingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    TextButton(onClick = {
                        scope.launch {
                            loadingModels = true
                            runCatching { api.fetchModels("video") }.onSuccess { if (it.isNotEmpty()) models = it }
                                .onFailure { error = it.message ?: "دریافت مدل‌ها ناموفق بود." }
                            loadingModels = false
                        }
                    }) { Icon(Icons.Default.Refresh, null); Text("به‌روزرسانی مدل‌ها") }
                }
            }
        }

        Text("۱. مدل ویدیوساز", color = Color.White, fontWeight = FontWeight.Bold)
        models.forEach { model ->
            Surface(
                onClick = { if (!generating) selectedModel = model },
                color = if (selectedModel == model) Color(0xFF263342) else panel,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedModel == model) Color(0xFF9DB5CC) else Color(0xFF303946))
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedModel == model, onClick = { selectedModel = model }, enabled = !generating)
                    Text(model, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
        Text("فهرست از API دریافت می‌شود. مدل‌های قابل استفاده و محدودیت‌های مدت و قاب ممکن است با تغییر کاتالوگ عوض شوند.", color = muted, style = MaterialTheme.typography.labelSmall)

        Text("۲. توضیح ویدیو", color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it.take(4000) },
            modifier = Modifier.fillMaxWidth(), minLines = 4,
            placeholder = { Text("صحنه، حرکت دوربین، نورپردازی و سبک ویدیو را دقیق توضیح بده…") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF9DB5CC), unfocusedBorderColor = Color(0xFF46515F))
        )
        Text("${prompt.length}/4000 نویسه", color = muted, style = MaterialTheme.typography.labelSmall)

        Text("۳. عکس ورودی (اختیاری)", color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = !generating, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ImageIcon, null); Spacer(Modifier.width(8.dp)); Text(if (imageUri == null) "انتخاب عکس مرجع" else "تعویض عکس مرجع")
        }
        if (imagePreview != null) {
            Image(bitmap = imagePreview!!, contentDescription = "عکس مرجع ویدیو", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            TextButton(onClick = { imageUri = null; imagePreview = null; imageBase64 = null }) { Text("حذف عکس") }
        }
        if (imageUri != null) Text("نکته: ورودی عکس فقط برای مدل‌هایی کار می‌کند که در کاتالوگ API قابلیت image_input دارند. مدل‌های Sora و Veo در این مسیر متن‌محورند.", color = Color(0xFFE7C98F), style = MaterialTheme.typography.labelSmall)

        Text("۴. تنظیمات خروجی", color = Color.White, fontWeight = FontWeight.Bold)
        Surface(color = panel, shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("مدت", color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(4, 6, 8, 12).forEach { value ->
                        FilterChip(selected = duration == value, onClick = { duration = value }, enabled = !generating, label = { Text("$value ثانیه") })
                    }
                }
                Text("کیفیت و جهت تصویر", color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("720p", "1080p", "4K").forEach { value ->
                        FilterChip(selected = resolution == value, onClick = { resolution = value }, enabled = !generating, label = { Text(value) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("9:16", "16:9").forEach { value ->
                        FilterChip(selected = ratio == value, onClick = { ratio = value }, enabled = !generating, label = { Text(value) })
                    }
                }
            }
        }
        Surface(color = Color(0xFF302719), shape = RoundedCornerShape(12.dp)) {
            Text("تولید ویدیو هزینه‌دار است. مدل‌ها محدودیت مدت و اندازه متفاوت دارند؛ مقدار نامعتبر پیش از رندر از سوی API رد می‌شود. پیش از تأیید، مدل و تنظیمات را بررسی کن.", color = Color(0xFFF0D6A6), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { confirmGeneration = true },
            enabled = !generating && apiKey.isNotBlank() && prompt.isNotBlank() && imageBase64 == null || (!generating && apiKey.isNotBlank() && prompt.isNotBlank() && imageBase64 != null),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCE3ED), contentColor = Color(0xFF111722))
        ) { Text("بررسی نهایی و شروع تولید") }

        if (generating) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("در حال تولید: $progress٪", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(status, color = muted, style = MaterialTheme.typography.bodySmall)
        if (error.isNotBlank()) Text(error, color = Color(0xFFFFB2A8), style = MaterialTheme.typography.bodySmall)
        if (pendingVideo != null) {
            Button(onClick = { saveVideo.launch("Supra-Pro-${System.currentTimeMillis()}.mp4") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("ذخیره ویدیوی نهایی MP4")
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmGeneration) {
        AlertDialog(
            onDismissRequest = { confirmGeneration = false },
            title = { Text("تأیید تولید پولی") },
            text = { Text("مدل: $selectedModel\nمدت: $duration ثانیه\nکیفیت: $resolution\nنسبت: $ratio\n\nبا شروع درخواست، در صورت پذیرش توسط سرویس هزینه از اعتبار نت‌ارز کسر می‌شود. ادامه می‌دهی؟") },
            confirmButton = {
                TextButton(onClick = {
                    confirmGeneration = false
                    generating = true; progress = 0; error = ""; pendingVideo = null
                    status = "در حال ارسال درخواست به نت‌ارز…"
                    scope.launch {
                        try {
                            val size = when (ratio) {
                                "9:16" -> if (resolution == "4K") "2160x3840" else if (resolution == "1080p") "1080x1920" else "720x1280"
                                else -> if (resolution == "4K") "3840x2160" else if (resolution == "1080p") "1920x1080" else "1280x720"
                            }
                            val job = api.createVideo(selectedModel, prompt, duration, size, imageBase64)
                            estimatedCost = job.estimatedUsd
                            status = "درخواست پذیرفته شد؛ هزینه اعلام‌شده: ${job.estimatedUsd.ifBlank { "در پاسخ اولیه اعلام نشد" }} دلار. شناسه: ${job.id}"
                            val bytes = api.waitForVideo(job.id) { update ->
                                withContext(Dispatchers.Main) {
                                    progress = update.progress
                                    status = when (update.status) {
                                        "queued" -> "در صف پردازش…"
                                        "in_progress" -> "در حال رندر ویدیو…"
                                        else -> "وضعیت: ${update.status}"
                                    }
                                }
                            }
                            pendingVideo = bytes
                            progress = 100
                            status = "ویدیو آماده است؛ برای ذخیره MP4 دکمه پایین را بزن."
                        } catch (e: Exception) {
                            error = e.message ?: "تولید ویدیو ناموفق بود."
                            status = "فرآیند تولید کامل نشد."
                        } finally {
                            generating = false
                        }
                    }
                }) { Text("بله، تولید کن") }
            },
            dismissButton = { TextButton(onClick = { confirmGeneration = false }) { Text("انصراف") } }
        )
    }
    if (showAccount) {
        AlertDialog(
            onDismissRequest = { showAccount = false },
            title = { Text("اعتبار نت‌ارز") },
            text = { Text("موجودی: $accountBalance") },
            confirmButton = { TextButton(onClick = { showAccount = false }) { Text("باشه") } }
        )
    }
}
