package com.naeem.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PIXVERSE_URL = "https://app.pixverse.ai/"
private fun videoDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "فایل انتخاب‌شده"
}

private fun decodeVideoReferenceBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxDimension = 1440
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}

@Composable
fun VideoCreatorDashboard(onClose: () -> Unit, initialPrompt: String = "") {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf(initialPrompt) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var outputName by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // GetContent uses the system media chooser and grants temporary read access.
        // The selected image is consumed immediately and is not persisted by this screen.
        imageUri = uri
    }
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val name = videoDisplayName(context, uri)
            val isVideo = mime.startsWith("video/") || name.endsWith(".mp4", true) ||
                name.endsWith(".mov", true) || name.endsWith(".m4v", true) || name.endsWith(".webm", true)
            if (!isVideo) {
                Toast.makeText(context, "این فایل ویدیو نیست؛ لطفاً فایل MP4 یا ویدیویی انتخاب کن.", Toast.LENGTH_LONG).show()
            } else {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                outputUri = uri
                outputName = name
            }
        }
    }

    val imageBitmap by produceState<Bitmap?>(initialValue = null, imageUri) {
        value = imageUri?.let { uri ->
            withContext(Dispatchers.IO) { decodeVideoReferenceBitmap(context, uri) }
        }
    }

    fun openVideoService() {
        if (prompt.isBlank() && imageUri == null) {
            Toast.makeText(context, "اول توضیح ویدیو یا عکس مرجع را وارد کن.", Toast.LENGTH_SHORT).show()
            return
        }
        if (prompt.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Supra video prompt", prompt.trim()))
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PIXVERSE_URL)))
            Toast.makeText(
                context,
                if (imageUri != null) "توضیح کپی شد؛ عکس مرجع را در صفحه سرویس بارگذاری کن." else "توضیح ویدیو کپی شد؛ آن را در صفحه سرویس وارد کن.",
                Toast.LENGTH_LONG
            ).show()
        }.onFailure {
            Toast.makeText(context, "بازکردن صفحه ساخت ویدیو ممکن نشد.", Toast.LENGTH_LONG).show()
        }
    }

    fun copyPrompt() {
        if (prompt.isBlank()) {
            Toast.makeText(context, "اول توضیح حرکت را بنویس.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Supra video prompt", prompt.trim()))
        Toast.makeText(context, "توضیح ویدیو کپی شد.", Toast.LENGTH_SHORT).show()
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
                Text("اتصال به سرویس ساخت ویدیو", color = Color(0xFF9DA8B7), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.MovieCreation, contentDescription = null, tint = Color(0xFFD4D9E0), modifier = Modifier.size(27.dp))
        }

        Surface(color = Color(0xFF17251F), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF477A5B))) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ساخت ویدیو در سرویس بیرونی انجام می‌شود", color = Color(0xFFB9E5C9), fontWeight = FontWeight.Bold)
                Text("این صفحه به‌تنهایی ویدیو تولید نمی‌کند. توضیحت کپی می‌شود و صفحه سرویس باز خواهد شد؛ عکس مرجع را آنجا بارگذاری کن و پس از ساخت، فایل MP4 را از بخش پایین وارد کن.", color = Color(0xFFD0DBD2), style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("۱. عکس مرجع", color = Color.White, fontWeight = FontWeight.Bold)
        if (imageUri == null) {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ImageIcon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("انتخاب عکس از گوشی")
            }
        } else {
            Surface(color = Color(0xFF171C24), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap!!.asImageBitmap(),
                            contentDescription = "عکس مرجع",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(videoDisplayName(context, imageUri!!), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                            Text("تعویض عکس")
                        }
                        TextButton(onClick = { imageUri = null }) { Text("حذف") }
                    }
                }
            }
        }

        Text("۲. توضیح حرکت و دوربین", color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
            placeholder = { Text("مثلاً: دوربین آرام به سمت سوژه حرکت کند، نور طبیعی تغییر کند و جزئیات اصلی تصویر ثابت بمانند...") },
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
            Text("کپی توضیح حرکت")
        }

        Text("۳. ساخت و دریافت ویدیو", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "با زدن دکمه، صفحه سرویس باز می‌شود. توضیح ویدیو در حافظه کپی شده و عکس انتخابی را باید در همان صفحه بارگذاری کنی. بعد از دانلود MP4، آن را در مرحله بعد وارد کن.",
            color = Color(0xFFB8C3D1), style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = { openVideoService() },
            enabled = imageUri != null || prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4D9E0), contentColor = Color(0xFF10151C)),
            shape = RoundedCornerShape(13.dp)
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("باز کردن سرویس ساخت ویدیو")
        }
        Text("ساخت واقعی و دریافت MP4 در سرویس بیرونی انجام می‌شود؛ این برنامه فایل عکس را به‌عنوان ویدیو ثبت نمی‌کند.", color = Color(0xFF9DA8B7), style = MaterialTheme.typography.labelSmall)

        Divider(color = Color(0xFF343C48))
        Text("۴. واردکردن ویدیوی نهایی", color = Color.White, fontWeight = FontWeight.Bold)
        Text("فقط فایل ویدیویی انتخاب می‌شود؛ عکس یا تصویر ثابت به‌عنوان ویدیو پذیرفته نخواهد شد.", color = Color(0xFF9DA8B7), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { outputPicker.launch(arrayOf("video/mp4", "video/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.UploadFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("انتخاب ویدیوی ساخته‌شده (MP4)")
        }
        if (outputUri != null) {
            Surface(color = Color(0xFF17251F), shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF8BC6A0))
                    Spacer(Modifier.width(8.dp))
                    Text(outputName, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, outputUri)
                        clipData = ClipData.newUri(context.contentResolver, outputName, outputUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "اشتراک ویدیوی Supra")) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("اشتراک ویدیوی خروجی") }
        }

        Text(
            "حریم خصوصی: عکس و متن با انتخاب تو به سرویس بیرونی فرستاده می‌شوند. سهمیه سه‌تایی فقط روی همین نصب برنامه ذخیره می‌شود؛ برای سهمیه ضدتقلب و یکسان بین چند گوشی، سرور اختصاصی لازم است.",
            color = Color(0xFF8793A2), style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(18.dp))
    }
}
