package com.naeem.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfessionalVideoDashboard(onClose: () -> Unit, baseUrl: String, apiKey: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secureStore = remember(context) { SecureConfigStore(context) }
    var localKey by remember(baseUrl) { mutableStateOf(apiKey.ifBlank { secureStore.apiKeyFor(baseUrl) }) }
    var keySaved by remember(baseUrl) { mutableStateOf(localKey.isNotBlank()) }
    val api = remember(baseUrl, localKey) { NetArzApiClient(baseUrl, localKey) }
    var models by remember { mutableStateOf<List<NetArzVideoModel>>(emptyList()) }
    var selected by remember { mutableStateOf<NetArzVideoModel?>(null) }
    var prompt by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(8) }
    var size by remember { mutableStateOf("720x1280") }
    var ratio by remember { mutableStateOf("9:16") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageData by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("یک مدل را انتخاب کن.") }
    var error by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var resultBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun refreshModels() {
        scope.launch {
            loading = true
            error = ""
            runCatching { api.videoModelCatalog() }
                .onSuccess { models = it; status = if (it.isEmpty()) "مدل فعالی در فهرست نیست." else "مدل موردنظرت را انتخاب کن." }
                .onFailure { error = it.message ?: "دریافت فهرست مدل‌ها ناموفق بود." }
            loading = false
        }
    }
    LaunchedEffect(baseUrl, localKey) { refreshModels() }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUri = uri
            imageData = null
            error = ""
            scope.launch {
                runCatching { NetArzApiClient.imageAsBase64(context, uri) }
                    .onSuccess { imageData = it; error = "" }
                    .onFailure { imageData = null; error = it.message ?: "خواندن تصویر ناموفق بود." }
            }
        }
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val bytes = resultBytes
        if (uri != null && bytes != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                        ?: throw IllegalStateException("محل ذخیره فایل باز نشد.")
                }
            }.onSuccess { status = "ویدیو با موفقیت ذخیره شد." }
                .onFailure { error = it.message ?: "ذخیره ویدیو ناموفق بود." }
            resultBytes = null
        }
    }

    val bg = Color(0xFF0B0E13)
    val panel = Color(0xFF171D27)
    val muted = Color(0xFF9DA8B7)
    val silver = Color(0xFFDCE3ED)

    Column(Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (selected != null && !running) { selected = null; imageUri = null; imageData = null; error = "" }
                else if (!running) onClose()
            }, enabled = !running) { Icon(Icons.Default.ArrowBack, "بازگشت", tint = silver) }
            Column(Modifier.weight(1f)) {
                Text("ویدیوساز هوشیار", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(selected?.name ?: "انتخاب مدل ساخت ویدیو", color = muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFD6C08A))
        }

        Surface(color = panel, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("کلید API", color = Color.White, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = localKey, onValueChange = { localKey = it; keySaved = false },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("کلید API مربوطه را وارد کنید") },
                    keyboardOptions = KeyboardOptions.Default,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = silver, unfocusedBorderColor = Color(0xFF46515F))
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (keySaved) "کلید در حافظه امن دستگاه ذخیره شده است." else "برای ادامه، کلید را ذخیره کنید.", color = if (keySaved) Color(0xFF9BD2A9) else muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Button(enabled = localKey.isNotBlank() && !keySaved, onClick = {
                        secureStore.rememberApiKey(baseUrl, localKey); keySaved = true; refreshModels()
                    }) { Text("ذخیره کلید") }
                }
                Text("برای دریافت کلید، با پشتیبانی تماس بگیرید.", color = muted, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (selected == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مدل موردنظرت را انتخاب کن", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("با انتخاب هر مدل، وارد صفحه تنظیمات اختصاصی آن می‌شوی.", color = muted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { refreshModels() }, enabled = !loading) { Icon(Icons.Default.Refresh, "به‌روزرسانی", tint = silver) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (models.isEmpty() && !loading) {
                Text("فهرست مدل‌ها خالی است یا دریافت نشده؛ اتصال را بررسی و دوباره تلاش کن.", color = muted)
            } else if (models.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showModelPicker = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = silver)
                ) {
                    Text("انتخاب از فهرست مدل‌ها", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                Text("تعداد مدل‌های در دسترس: ${models.size}", color = muted, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            val model = selected!!
            Surface(color = Color(0xFF17271F), shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(model.name, color = Color(0xFFBCE4C5), fontWeight = FontWeight.Bold)
                    Text(model.description.ifBlank { "تنظیمات این صفحه بر اساس قابلیت‌های اعلام‌شده برای این مدل نمایش داده می‌شود." }, color = Color(0xFFD2DBD4), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("توضیح ویدیو", color = Color.White, fontWeight = FontWeight.Bold)
            OutlinedTextField(prompt, { prompt = it.take(4000) }, modifier = Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("صحنه، سوژه، حرکت دوربین، نور و سبک ویدیو را توضیح بده…") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = silver, unfocusedBorderColor = Color(0xFF46515F)))
            Text("${prompt.length}/4000 نویسه", color = muted, style = MaterialTheme.typography.labelSmall)

            if (model.imageInput) {
                Text("تصویر ورودی", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ImageIcon, null); Spacer(Modifier.width(7.dp)); Text(if (imageUri == null) "انتخاب تصویر مرجع" else "تعویض تصویر مرجع")
                }
                if (imageUri != null) TextButton(onClick = { imageUri = null; imageData = null }) { Text("حذف تصویر") }
                Text("در اتصال فعلی، این مدل یک تصویر ورودی می‌پذیرد.", color = muted, style = MaterialTheme.typography.labelSmall)
            }

            Text("تنظیمات خروجی", color = Color.White, fontWeight = FontWeight.Bold)
            Surface(color = panel, shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.seconds.isNotEmpty()) {
                        Text("مدت", color = muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            model.seconds.forEach { v -> FilterChip(selected = seconds == v, onClick = { seconds = v }, enabled = !running, label = { Text("${v} ثانیه") }) }
                        }
                    }
                    if (model.sizes.isNotEmpty()) {
                        Text("اندازه", color = muted)
                        model.sizes.chunked(3).forEach { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                group.forEach { v -> FilterChip(selected = size == v, onClick = { size = v }, enabled = !running, label = { Text(v) }) }
                            }
                        }
                    }
                    if (model.audio) Text("خروجی این مدل همراه صداست.", color = Color(0xFF9BD2A9), style = MaterialTheme.typography.labelSmall)
                }
            }
            Button(
                onClick = { confirm = true },
                enabled = !running && keySaved && localKey.isNotBlank() && prompt.isNotBlank() &&
                    (model.seconds.isEmpty() || seconds in model.seconds) &&
                    (model.sizes.isEmpty() || size in model.sizes) &&
                    (!model.imageInput || imageData != null),
                modifier = Modifier.fillMaxWidth().height(51.dp),
                colors = ButtonDefaults.buttonColors(containerColor = silver, contentColor = Color(0xFF111722))
            ) { Text("بررسی نهایی و شروع تولید") }
        }

        if (running) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("در حال تولید: $progress٪", color = Color.White)
        }
        if (status.isNotBlank()) Text(status, color = muted, style = MaterialTheme.typography.bodySmall)
        if (error.isNotBlank()) Text(error, color = Color(0xFFFFB2A8), style = MaterialTheme.typography.bodySmall)
        if (resultBytes != null) Button(onClick = { savePicker.launch("Supra-Video-${System.currentTimeMillis()}.mp4") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(7.dp)); Text("ذخیره ویدیوی نهایی")
        }
    }

    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("انتخاب مدل تولید ویدیو") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    models.forEach { item ->
                        Surface(
                            onClick = {
                                selected = item
                                seconds = item.seconds.firstOrNull() ?: 8
                                size = item.sizes.firstOrNull() ?: "720x1280"
                                prompt = ""
                                imageUri = null
                                imageData = null
                                error = ""
                                showModelPicker = false
                            },
                            color = panel,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF303946))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                if (item.description.isNotBlank()) {
                                    Text(item.description, color = muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                                Text(
                                    buildList {
                                        if (item.imageInput) add("ورودی تصویر")
                                        if (item.audio) add("همراه صدا")
                                        if (item.seconds.isNotEmpty()) add(item.seconds.joinToString("، ") + " ثانیه")
                                    }.joinToString(" • ").ifBlank { "ساخت ویدیو از متن" },
                                    color = Color(0xFFB7C7D9), style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("بستن") } }
        )
    }

    if (confirm && selected != null) {
        val model = selected!!
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("تأیید تولید") },
            text = { Text("مدل: ${model.name}\nمدت: $seconds ثانیه\nاندازه: $size\nنسبت: $ratio\n\nبا شروع درخواست، هزینه از اعتبار API کسر می‌شود. ادامه می‌دهی؟") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false; running = true; progress = 0; error = ""; resultBytes = null; status = "در حال ارسال درخواست…"
                    scope.launch {
                        try {
                            val job = api.createVideo(model.id, prompt, seconds, size, imageData)
                            status = "درخواست پذیرفته شد؛ هزینه اعلام‌شده: ${job.estimatedUsd.ifBlank { "اعلام نشده" }} دلار."
                            val bytes = api.waitForVideo(job.id) { update ->
                                withContext(Dispatchers.Main) {
                                    progress = update.progress
                                    status = when (update.status) {
                                        "queued" -> "در صف پردازش…"
                                        "in_progress" -> "در حال ساخت ویدیو…"
                                        else -> "وضعیت: ${update.status}"
                                    }
                                }
                            }
                            resultBytes = bytes; progress = 100; status = "ویدیو آماده است؛ آن را ذخیره کن."
                        } catch (e: Exception) {
                            error = e.message ?: "تولید ویدیو ناموفق بود."; status = "فرآیند تولید کامل نشد."
                        } finally { running = false }
                    }
                }) { Text("بله، تولید کن") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } }
        )
    }
}
