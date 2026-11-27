package com.naeem.ai

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
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

private data class StudioClip(val uri: Uri, val name: String, val durationMs: Long, val trimStartMs: Long = 0L, val trimEndMs: Long = durationMs, val speed: Float = 1f)

private fun studioDurationMs(context: Context, uri: Uri): Long = runCatching {
    MediaMetadataRetriever().let { retriever ->
        try { retriever.setDataSource(context, uri); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
        finally { retriever.release() }
    }
}.getOrDefault(0L)

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
    var muteOriginalAudio by remember { mutableStateOf(false) }
    var musicUri by remember { mutableStateOf<Uri?>(null) }
    var musicName by remember { mutableStateOf("") }
    var musicDurationMs by remember { mutableLongStateOf(0L) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
    val previewClip = clips.getOrNull(selectedIndex)
    val previewUri = previewClip?.uri
    val player = remember(previewClip) {
        previewClip?.let { clip ->
            val previewItem = MediaItem.Builder().setUri(clip.uri)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.trimStartMs).setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 1L)).build()).build()
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(previewItem)
                prepare()
                playWhenReady = false
            }
        }
    }

    LaunchedEffect(player, previewClip?.speed) { player?.setPlaybackSpeed(previewClip?.speed ?: 1f) }

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
            if (clips.none { it.uri == uri }) {
                val duration = studioDurationMs(context, uri)
                clips.add(StudioClip(uri, studioDisplayName(context, uri), duration))
            }
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
            val clippedItem = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L)).setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 1L)).build()
            ).build()
            EditedMediaItem.Builder(clippedItem)
                .setRemoveAudio(muteOriginalAudio)
                .setSpeed(object : SpeedProvider {
                    override fun getSpeed(presentationTimeUs: Long): Float = clip.speed
                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                })
                .build()
        }
        val videoSequence = EditedMediaItemSequence.Builder().addItems(editedItems).build()
        val sequences = mutableListOf(videoSequence)
        musicUri?.let { uri ->
            val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveVideo(true).build()
            sequences.add(EditedMediaItemSequence.Builder().addItem(musicItem).build())
        }
        val composition = Composition.Builder(sequences).build()
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    scope.launch {
                        exportStatus = "در حال ذخیره فایل نهایی…"
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val output = context.contentResolver.openOutputStream(destination, "w") ?: throw IllegalStateException("محل ذخیره‌سازی باز نشد.")
                                output.use { stream -> outputFile.inputStream().use { input -> input.copyTo(stream) } }
                            }
                        }.onSuccess {
                            exporting = false; activeTransformer = null; exportPercent = 100
                            exportStatus = "خروجی MP4 با موفقیت ذخیره شد."
                        }.onFailure { error ->
                            exporting = false; activeTransformer = null
                            exportStatus = "تدوین انجام شد اما ذخیره فایل ناموفق بود: ${error.message ?: "خطای دسترسی به محل ذخیره"}"
                        }
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

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onExternalActivityReturned()
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            musicUri = uri
            musicName = studioDisplayName(context, uri)
            musicDurationMs = studioDurationMs(context, uri)
            exportStatus = "موسیقی پس‌زمینه اضافه شد."
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { destination ->
        onExternalActivityReturned()
        if (destination != null) beginExport(destination)
    }

    Column(
        Modifier.fillMaxSize().background(Color.Transparent)
            .verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !exporting) {
                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("EMA ASI PLATFORM", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
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
                            .background(SupraGlass.Glass, RoundedCornerShape(14.dp)),
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
                colors = ButtonDefaults.buttonColors(containerColor = SupraGlass.Ice, contentColor = Color(0xFF101722)),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("افزودن ویدیو")
            }
            OutlinedButton(
                onClick = { if (clips.isNotEmpty()) { onExternalActivityStarted(); exportLauncher.launch("ImaStudio.mp4") } },
                enabled = clips.isNotEmpty() && !exporting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("خروجی MP4")
            }
        }

        Surface(color = Color(0xFF111B29), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("لایه موسیقی و صدا", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(musicName.ifBlank { "هنوز موسیقی پس‌زمینه انتخاب نشده" }, color = Color(0xFF9EACBD), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    OutlinedButton(onClick = { onExternalActivityStarted(); musicPicker.launch(arrayOf("audio/*")) }, enabled = !exporting) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("افزودن موسیقی")
                    }
                }
                if (musicUri != null) {
                    Text("موسیقی به‌صورت لایه مستقل روی ویدیو میکس و در خروجی MP4 قرار می‌گیرد.", color = Color(0xFF9ED7B2), style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { musicUri = null; musicName = ""; musicDurationMs = 0L }) { Text("حذف موسیقی") }
                }
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

        clips.getOrNull(selectedIndex)?.let { clip ->
            Surface(color = Color(0xFF111B29), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("برش کلیپ انتخاب‌شده", color = Color.White, fontWeight = FontWeight.Bold)
                    if (clip.durationMs > 1000L) {
                        Text("شروع: ${clip.trimStartMs / 1000.0}s", color = Color(0xFFB8C9E4), style = MaterialTheme.typography.labelMedium)
                        Slider(value = clip.trimStartMs.toFloat().coerceIn(0f, (clip.durationMs - 500L).toFloat()), onValueChange = { value ->
                            clips[selectedIndex] = clip.copy(trimStartMs = value.toLong().coerceIn(0L, clip.trimEndMs - 500L))
                        }, valueRange = 0f..(clip.durationMs - 500L).toFloat(), enabled = !exporting)
                        Text("پایان: ${clip.trimEndMs / 1000.0}s از ${clip.durationMs / 1000.0}s", color = Color(0xFFB8C9E4), style = MaterialTheme.typography.labelMedium)
                        Slider(value = clip.trimEndMs.toFloat().coerceIn((clip.trimStartMs + 500L).toFloat(), clip.durationMs.toFloat()), onValueChange = { value ->
                            clips[selectedIndex] = clip.copy(trimEndMs = value.toLong().coerceIn(clip.trimStartMs + 500L, clip.durationMs))
                        }, valueRange = (clip.trimStartMs + 500L).toFloat()..clip.durationMs.toFloat(), enabled = !exporting)
                    } else Text("مدت این فایل قابل تشخیص نیست؛ برش برای آن غیرفعال است.", color = Color(0xFFE5C58A), style = MaterialTheme.typography.bodySmall)
                    Text("سرعت پخش ویدیو", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                            FilterChip(selected = clip.speed == rate, onClick = {
                                clips[selectedIndex] = clip.copy(speed = rate)
                            }, enabled = !exporting, label = { Text("${rate}x") })
                        }
                    }
                    Text("سرعت انتخابی هم در پیش‌نمایش و هم در خروجی اعمال می‌شود.", color = Color(0xFF9EACBD), style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("بی‌صدا کردن صدای اصلی", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text("برای همه کلیپ‌های پروژه", color = Color(0xFF8996A8), style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = muteOriginalAudio, onCheckedChange = { muteOriginalAudio = it }, enabled = !exporting)
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
                Text("ابزارهای فعال این نسخه", color = Color.White, fontWeight = FontWeight.Bold)
                Text("چند کلیپ در تایم‌لاین، پیش‌نمایش، جابه‌جایی ترتیب، حذف، برش ابتدا و انتها، سرعت ۰٫۵ تا ۲ برابر، لایه موسیقی مستقل، کنترل صدای اصلی و خروجی MP4. فیلترهای تصویری، متن و زیرنویس، استیکر، ترنزیشن و چندلایه تصویری در مرحله بعدی توسعه قرار دارند.", color = Color(0xFF9EACBD), style = MaterialTheme.typography.bodySmall)
                Text("پردازش روی دستگاه انجام می‌شود؛ فایل‌ها برای تدوین به سرور ارسال نمی‌شوند.", color = Color(0xFF9ED7B2), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
