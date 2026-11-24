package com.naeem.ai

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class StudioClip(val uri: Uri, val name: String)

private fun studioDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "ویدیوی انتخاب‌شده"
}

@OptIn(UnstableApi::class)
@Composable
fun SupraStudioDashboard(
    onClose: () -> Unit,
    onExternalActivityStarted: () -> Unit,
    onExternalActivityReturned: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clips = remember { mutableStateListOf<StudioClip>() }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf("برای شروع، ویدیوهایت را وارد کن.") }
    var exportPercent by remember { mutableIntStateOf(0) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
    val previewUri = clips.getOrNull(selectedIndex)?.uri
    val player = remember(previewUri) {
        previewUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = false
            }
        }
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        onExternalActivityReturned()
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (clips.none { it.uri == uri }) clips.add(StudioClip(uri, studioDisplayName(context, uri)))
        }
        if (clips.isNotEmpty()) {
            selectedIndex = selectedIndex.coerceIn(0, clips.lastIndex)
            exportStatus = "${clips.size} کلیپ در پروژه قرار دارد."
        }
    }

    fun beginExport(destination: Uri) {
        if (clips.isEmpty()) {
            exportStatus = "اول حداقل یک ویدیو وارد کن."
            return
        }
        exporting = true
        exportPercent = 0
        exportStatus = "در حال آماده‌سازی پروژه…"
        val outputFile = File(context.cacheDir, "supra_studio_${System.currentTimeMillis()}.mp4")
        val editedItems = clips.map { clip ->
            EditedMediaItem.Builder(MediaItem.fromUri(clip.uri)).build()
        }
        val sequence = EditedMediaItemSequence.Builder()
            .addItems(editedItems)
            .build()
        val composition = Composition.Builder(listOf(sequence)).build()
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    scope.launch {
                        exportStatus = "در حال ذخیره فایل نهایی…"
                        val saved = withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                                outputFile.inputStream().use { input -> input.copyTo(output) }
                            } ?: throw IllegalStateException("محل ذخیره‌سازی باز نشد.")
                            true
                        }
                        exporting = false
                        activeTransformer = null
                        exportPercent = 100
                        exportStatus = if (saved) "خروجی MP4 با موفقیت ذخیره شد." else "ذخیره خروجی ناموفق بود."
                        outputFile.delete()
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    exporting = false
                    activeTransformer = null
                    exportStatus = "تدوین ناموفق بود: ${exportException.message ?: "فرمت یکی از ویدیوها پشتیبانی نشد."}"
                    outputFile.delete()
                }
            })
            .build()
        activeTransformer = transformer
        runCatching {
            transformer.start(composition, outputFile.absolutePath)
            exportStatus = "در حال تدوین و ترکیب کلیپ‌ها…"
        }.onFailure {
            exporting = false
            activeTransformer = null
            exportStatus = "شروع خروجی ناموفق بود: ${it.message ?: "خطای ناشناخته"}"
            outputFile.delete()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { destination ->
        onExternalActivityReturned()
        if (destination != null) beginExport(destination)
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF090D13))
            .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !exporting) {
                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("SUPRA STUDIO", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("استودیوی تدوین ویدیو", color = Color(0xFF9EACBD), style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF9CB9F4), modifier = Modifier.size(28.dp))
        }

        Surface(color = Color(0xFF111B29), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("پیش‌نمایش", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${clips.size} کلیپ", color = Color(0xFF9CB9F4), style = MaterialTheme.typography.labelMedium)
                }
                if (player != null) {
                    AndroidView(
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                this.player = player
                                useController = true
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (viewContext.resources.displayMetrics.widthPixels * 0.52f).toInt())
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxWidth().height(205.dp)
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(205.dp)
                            .background(Color(0xFF080B10), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF8296B4), modifier = Modifier.size(42.dp))
                            Text("پیش‌نمایش پروژه اینجا نمایش داده می‌شود", color = Color(0xFF8795A8), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onExternalActivityStarted(); importLauncher.launch(arrayOf("video/*")) },
                enabled = !exporting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCAD8F0), contentColor = Color(0xFF101722)),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("افزودن ویدیو")
            }
            OutlinedButton(
                onClick = { if (clips.isNotEmpty()) { onExternalActivityStarted(); exportLauncher.launch("SupraStudio.mp4") } },
                enabled = clips.isNotEmpty() && !exporting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("خروجی MP4")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("تایم‌لاین پروژه", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("برای تغییر ترتیب از فلش‌ها استفاده کن", color = Color(0xFF8996A8), style = MaterialTheme.typography.labelSmall)
        }

        if (clips.isEmpty()) {
            Surface(color = Color(0xFF141A23), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                Text("هنوز کلیپی اضافه نشده است. می‌توانی چند ویدیو را با هم انتخاب کنی تا پشت سر هم مونتاژ شوند.", color = Color(0xFFAAB6C6), modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            clips.forEachIndexed { index, clip ->
                Surface(
                    color = if (index == selectedIndex) Color(0xFF1B2A3D) else Color(0xFF141A23),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (index == selectedIndex) Color(0xFF7897C1) else Color(0xFF293241)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = Color(0xFF9CB9F4), fontWeight = FontWeight.Bold, modifier = Modifier.width(25.dp))
                        Text(clip.name, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = {
                            if (index > 0) {
                                val item = clips.removeAt(index)
                                clips.add(index - 1, item)
                                if (selectedIndex == index) selectedIndex = index - 1
                                else if (selectedIndex == index - 1) selectedIndex = index
                            }
                        }, enabled = index > 0 && !exporting) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "بالا", tint = Color(0xFFB8C9E4))
                        }
                        IconButton(onClick = {
                            if (index < clips.lastIndex) {
                                val item = clips.removeAt(index)
                                clips.add(index + 1, item)
                                if (selectedIndex == index) selectedIndex = index + 1
                                else if (selectedIndex == index + 1) selectedIndex = index
                            }
                        }, enabled = index < clips.lastIndex && !exporting) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "پایین", tint = Color(0xFFB8C9E4))
                        }
                        IconButton(onClick = {
                            clips.removeAt(index)
                            selectedIndex = selectedIndex.coerceAtMost((clips.size - 1).coerceAtLeast(0))
                        }, enabled = !exporting) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFE29A9A))
                        }
                    }
                }
            }
        }

        if (exporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF9CB9F4))
        }
        Text(exportStatus, color = if (exportStatus.contains("موفقیت")) Color(0xFF9ED7B2) else Color(0xFFAAB6C6), style = MaterialTheme.typography.bodySmall)

        Surface(color = Color(0xFF111821), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("مرحله اول تدوینگر", color = Color.White, fontWeight = FontWeight.Bold)
                Text("ورود چند کلیپ، پیش‌نمایش، تغییر ترتیب، حذف و مونتاژ واقعی روی گوشی با خروجی MP4. در نسخه‌های بعدی، برش دقیق، موسیقی جداگانه، متن و زیرنویس، فیلترها و لایه‌های تصویری به همین تایم‌لاین اضافه می‌شوند.", color = Color(0xFF9EACBD), style = MaterialTheme.typography.bodySmall)
                Text("پردازش روی دستگاه انجام می‌شود؛ فایل‌ها برای تدوین به سرور ارسال نمی‌شوند.", color = Color(0xFF9ED7B2), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
