package com.naeem.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ProVideoModel(val id: String, val title: String, val note: String)

@Composable
fun ProfessionalVideoDashboard(onClose: () -> Unit) {
    val models = listOf(
        ProVideoModel("sora-2", "Sora 2", "ساخت ویدیو از توضیح متنی"),
        ProVideoModel("veo-3.1", "Veo 3.1", "تولید ویدیوی همراه صدا؛ کیفیت‌های بالاتر"),
        ProVideoModel("veo-3.1-fast", "Veo 3.1 Fast", "گزینه سریع‌تر برای تولید ویدیو")
    )
    var selectedModel by remember { mutableStateOf(models.first()) }
    var prompt by remember { mutableStateOf("") }
    var sourceMode by remember { mutableStateOf("عکس به ویدیو") }
    var duration by remember { mutableStateOf("۸ ثانیه") }
    var resolution by remember { mutableStateOf("720p") }
    var ratio by remember { mutableStateOf("9:16") }

    val bg = Color(0xFF0B0E13)
    val panel = Color(0xFF171D27)
    val muted = Color(0xFF9DA8B7)
    val silver = Color(0xFFDCE3ED)

    Column(
        Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = silver) }
            Column(Modifier.weight(1f)) {
                Text("ویدیوساز حرفه‌ای", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("تولید ویدیو از طریق API نت‌ارز", color = muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFD6C08A), modifier = Modifier.size(27.dp))
        }

        Surface(color = Color(0xFF202A36), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF526174))) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFE2C98F))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("اتصال API هنوز انجام نشده", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("بعد از خرید اعتبار و ثبت کلید API، تولید واقعی از همین صفحه فعال می‌شود. فعلاً هیچ هزینه‌ای از حسابت کم نمی‌شود.", color = Color(0xFFC1CAD5), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("۱. انتخاب مدل", color = Color.White, fontWeight = FontWeight.Bold)
        models.forEach { model ->
            val selected = selectedModel.id == model.id
            Surface(
                onClick = { selectedModel = model },
                color = if (selected) Color(0xFF263342) else panel,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color(0xFF9DB5CC) else Color(0xFF303946))
            ) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = { selectedModel = model })
                    Column(Modifier.weight(1f)) {
                        Text(model.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(model.note, color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("۲. نوع ورودی", color = Color.White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("عکس به ویدیو", "متن به ویدیو").forEach { mode ->
                FilterChip(selected = sourceMode == mode, onClick = { sourceMode = mode }, label = { Text(mode) })
            }
        }
        if (sourceMode == "عکس به ویدیو") {
            Text("انتخاب عکس مرجع پس از اتصال API فعال می‌شود.", color = muted, style = MaterialTheme.typography.bodySmall)
        }

        Text("۳. توضیح صحنه و حرکت", color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(), minLines = 4,
            placeholder = { Text("مثلاً دوربین به‌آرامی به سوژه نزدیک شود؛ نور و جزئیات اصلی تصویر حفظ شوند...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF9DB5CC), unfocusedBorderColor = Color(0xFF46515F)
            )
        )

        Text("۴. تنظیمات خروجی", color = Color.White, fontWeight = FontWeight.Bold)
        Surface(color = panel, shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("مدت ویدیو", color = muted, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("۵ ثانیه", "۸ ثانیه", "۱۰ ثانیه").forEach { value ->
                        FilterChip(selected = duration == value, onClick = { duration = value }, label = { Text(value) })
                    }
                }
                Text("کیفیت", color = muted, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("720p", "1080p", "4K").forEach { value ->
                        FilterChip(selected = resolution == value, onClick = { resolution = value }, label = { Text(value) })
                    }
                }
                Text("نسبت تصویر", color = muted, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("9:16", "16:9", "1:1").forEach { value ->
                        FilterChip(selected = ratio == value, onClick = { ratio = value }, label = { Text(value) })
                    }
                }
            }
        }

        Surface(color = Color(0xFF171F29), shape = RoundedCornerShape(13.dp)) {
            Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("پیکربندی انتخاب‌شده", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${selectedModel.title}  •  $duration  •  $resolution  •  $ratio", color = Color(0xFFC1CAD5), style = MaterialTheme.typography.bodySmall)
                Text("هزینه دقیق بعد از اتصال، از قیمت زنده API محاسبه و پیش از شروع نمایش داده می‌شود.", color = muted, style = MaterialTheme.typography.labelSmall)
            }
        }

        Button(
            onClick = { },
            enabled = false,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF39424D), disabledContentColor = Color(0xFFB9C2CD)),
            shape = RoundedCornerShape(13.dp)
        ) { Text("تولید ویدیو — پس از اتصال API فعال می‌شود") }

        Text("درگاه نت‌ارز از Sora و Veo و برخی مدل‌های ویدیویی OpenRouter پشتیبانی می‌کند. Genjutsu Object Swap یک مسیر جداگانه دارد و فقط پس از تأیید وجود مدل در کاتالوگ قابل اتصال است. کلید API برای نسخه عمومی باید روی سرور امن بماند.", color = muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(16.dp))
    }
}
