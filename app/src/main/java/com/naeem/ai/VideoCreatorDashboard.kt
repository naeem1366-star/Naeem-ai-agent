package com.naeem.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class FreeVideoProvider(
    val name: String,
    val detail: String,
    val freeMode: String,
    val url: String,
    val note: String
)

@Composable
fun VideoCreatorDashboard(onClose: () -> Unit, initialPrompt: String = "") {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf(initialPrompt) }
    val providers = listOf(
        FreeVideoProvider(
            "Wan2.Video",
            "گزینه اصلی؛ تبدیل متن یا تصویر به ویدیو با مدل‌های Wan و LTX",
            "رایگان با صف عمومی؛ بدون ثبت‌نام طبق توضیح سرویس",
            "https://wan2.video/",
            "ممکن است صف انتظار و محدودیت استفاده داشته باشد. برای عکس‌های خصوصی یا حساس استفاده نکن."
        ),
        FreeVideoProvider(
            "Raphael AI",
            "گزینه جایگزین؛ ساخت ویدیوی کوتاه از متن یا تصویر",
            "مدل رایگان MiniMax H3 Turbo با خروجی پایه ۴ ثانیه و 480p",
            "https://raphael.app/ai-video-generator",
            "شرایط رایگان ممکن است تغییر کند؛ پیش از Generate مطمئن شو مدل رایگان انتخاب شده و هزینه‌ای نمایش داده نمی‌شود."
        )
    )

    fun openProvider(provider: FreeVideoProvider) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            Toast.makeText(context, "مرورگر در دسترس نیست.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyPrompt() {
        if (prompt.isBlank()) {
            Toast.makeText(context, "اول توضیح ویدیوت را بنویس.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Video prompt", prompt.trim()))
        Toast.makeText(context, "پرامپت کپی شد؛ در سرویس ویدیو جای‌گذاری کن.", Toast.LENGTH_LONG).show()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0E13))
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = Color(0xFFDCE3ED))
            }
            Column(Modifier.weight(1f)) {
                Text("استودیو ساخت ویدیو", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("ابزارهای رایگان؛ بدون کلید API", color = Color(0xFF9DA8B7), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.MovieCreation, contentDescription = null, tint = Color(0xFFD4D9E0), modifier = Modifier.size(27.dp))
        }

        Surface(
            color = Color(0xFF18202B),
            shape = RoundedCornerShape(17.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF344253))
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("چطور کار می‌کند؟", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "Supra AI تو را به صفحه ساخت ویدیو در مرورگر می‌برد. متن یا تصویر را آنجا وارد می‌کنی و فایل MP4 را از همان سرویس می‌گیری. چون سرویس‌ها API رایگان و عمومی ارائه نمی‌کنند، ساخت ویدیو مستقیماً روی سرور Supra AI انجام نمی‌شود.",
                    color = Color(0xFFB8C3D1), style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text("سرویس‌های پیشنهادی", color = Color.White, fontWeight = FontWeight.Bold)
        providers.forEachIndexed { index, provider ->
            Surface(
                color = Color(0xFF171C24),
                shape = RoundedCornerShape(17.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (index == 0) Color(0xFF7189A8) else Color(0xFF343C48))
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        if (index == 0) Surface(color = Color(0xFF263B32), shape = RoundedCornerShape(20.dp)) {
                            Text("پیشنهاد اول", color = Color(0xFFB9E5C9), modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(provider.detail, color = Color(0xFFD1D7E0), style = MaterialTheme.typography.bodySmall)
                    Text(provider.freeMode, color = Color(0xFFB9D9C5), style = MaterialTheme.typography.labelMedium)
                    Text(provider.note, color = Color(0xFF9DA8B7), style = MaterialTheme.typography.labelSmall)
                    Button(
                        onClick = { openProvider(provider) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4D9E0), contentColor = Color(0xFF10151C)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("باز کردن سرویس")
                        Spacer(Modifier.width(7.dp))
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }

        Divider(color = Color(0xFF343C48))
        Text("دفترچه پرامپت ویدیو", color = Color.White, fontWeight = FontWeight.Bold)
        Text("ایده و حرکت دوربین را اینجا بنویس؛ بعد کپی کن و در سرویس انتخابی قرار بده.", color = Color(0xFF9DA8B7), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
            placeholder = { Text("مثلاً: دوربین آرام به سمت ویلا حرکت کند، درخت‌ها با باد ملایم تکان بخورند و نور عصر روی نما تغییر کند...") },
            minLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF9CB9D2),
                unfocusedBorderColor = Color(0xFF46515F),
                focusedPlaceholderColor = Color(0xFF7F8B9A),
                unfocusedPlaceholderColor = Color(0xFF7F8B9A),
                cursorColor = Color.White
            )
        )
        OutlinedButton(onClick = { copyPrompt() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("کپی پرامپت")
        }
        Text(
            "نکته حریم خصوصی: عکس‌ها و متن‌ها مستقیماً به سرویس انتخابی فرستاده می‌شوند؛ قبل از بارگذاری، سیاست حریم خصوصی همان سرویس را بررسی کن.",
            color = Color(0xFF8793A2), style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(18.dp))
    }
}
