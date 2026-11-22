package com.naeem.ai

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.os.Bundle
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

data class ChatMessage(val text: String, val fromUser: Boolean, val image: androidx.compose.ui.graphics.ImageBitmap? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val store = SecureConfigStore(this)
        setContent { NaeemApp(store) }
    }
}

private val Ink = Color(0xFF101114)
private val Panel = Color(0xFF1B1D22)
private val Gold = Color(0xFFD1B078)
private val Muted = Color(0xFF9A9DA6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaeemApp(store: SecureConfigStore) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(store.baseUrl()) }
    var model by remember { mutableStateOf(store.model()) }
    var apiKey by remember { mutableStateOf(store.apiKey()) }
    var imageModel by remember { mutableStateOf(store.imageModel()) }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var selectedImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var imageMode by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val client = remember { ChatClient() }
    val imageClient = remember { ImageGenerationClient() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("خواندن تصویر انتخاب‌شده ممکن نشد.")
                val scale = minOf(1f, 1280f / maxOf(source.width, source.height).toFloat())
                val resized = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true) else source
                val output = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 82, output)
                selectedImageBase64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                selectedImage = resized.asImageBitmap()
                imageMode = false
                error = ""
            } catch (e: Exception) {
                selectedImageBase64 = null
                selectedImage = null
                error = e.message ?: "خطا در آماده‌سازی تصویر"
            }
        }
    }
    val audioClient = remember { AudioSpeechClient() }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var conversationMode by remember { mutableStateOf(false) }
    val currentConversationMode by rememberUpdatedState(conversationMode)
    var startVoiceCapture by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun playAnswer(text: String, continueListening: Boolean = false) {
        scope.launch {
            var audioFile: File? = null
            try {
                val audioBytes = audioClient.synthesize(baseUrl, apiKey, text)
                audioFile = File(context.cacheDir, "naeem_tts_${System.currentTimeMillis()}.mp3")
                audioFile.writeBytes(audioBytes)
                mediaPlayer?.runCatching { stop() }
                mediaPlayer?.release()
                val player = MediaPlayer()
                mediaPlayer = player
                player.setDataSource(audioFile.absolutePath)
                player.setOnCompletionListener { completed ->
                    completed.release()
                    if (mediaPlayer === completed) mediaPlayer = null
                    audioFile?.delete()
                    if (continueListening && currentConversationMode && !busy) {
                        startVoiceCapture?.invoke()
                    }
                }
                player.setOnErrorListener { failed, _, _ ->
                    failed.release()
                    if (mediaPlayer === failed) mediaPlayer = null
                    audioFile?.delete()
                    error = "پخش صدای فارسی ناموفق بود."
                    true
                }
                player.prepare()
                player.start()
            } catch (e: Exception) {
                audioFile?.delete()
                error = e.message ?: "ساخت صدای فارسی ناموفق بود."
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false
        val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) {
            prompt = spoken
            val question = spoken.trim()
            prompt = ""
            messages.add(ChatMessage(question, true))
            busy = true
            error = ""
            scope.launch {
                try {
                    val answer = client.send(baseUrl, model, apiKey, question)
                    messages.add(ChatMessage(answer, false))
                    playAnswer(answer, continueListening = true)
                } catch (e: Exception) {
                    error = e.message ?: "خطای نامشخص"
                } finally { busy = false }
            }
        }
    }

    val beginListening = remember(speechLauncher) {
        {
            listening = true
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "صحبت کن…")
            }
            try { speechLauncher.launch(intent) }
            catch (_: Exception) { listening = false; error = "سرویس تشخیص گفتار روی گوشی در دسترس نیست." }
        }
    }
    SideEffect { startVoiceCapture = beginListening }


    fun sendMessage(speakAnswer: Boolean = false) {
        val question = prompt.trim()
        val imageToSend = selectedImageBase64
        val imagePreview = selectedImage
        if ((question.isBlank() && imageToSend.isNullOrBlank()) || busy) return
        val finalQuestion = question.ifBlank { "این تصویر را با جزئیات بررسی و تحلیل کن." }
        prompt = ""
        selectedImageBase64 = null
        selectedImage = null
        messages.add(ChatMessage(if (imageMode) "ساخت تصویر: $finalQuestion" else finalQuestion, true, imagePreview))
        busy = true
        error = ""
        scope.launch {
            try {
                if (imageMode) {
                    val bitmap = imageClient.generate(baseUrl, apiKey, imageModel, finalQuestion)
                    messages.add(ChatMessage("تصویر ساخته شد.", false, bitmap.asImageBitmap()))
                } else {
                    val answer = client.send(baseUrl, model, apiKey, finalQuestion, imageToSend)
                    messages.add(ChatMessage(answer, false))
                    if (speakAnswer) playAnswer(answer)
                }
            } catch (e: Exception) {
                error = e.message ?: "خطای نامشخص"
            } finally { busy = false }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Ink, background = Ink, onBackground = Color(0xFFF2F0EA),
            surface = Panel, onSurface = Color(0xFFF2F0EA), surfaceVariant = Color(0xFF292C33),
            onSurfaceVariant = Color(0xFFE0DED8), secondary = Color(0xFFB7A17C)
        )
    ) {
        Scaffold(
            containerColor = Ink,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Naeem AI", fontWeight = FontWeight.SemiBold)
                            Text("دستیار هوشمند شخصی", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "تنظیمات", tint = Gold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
                )
            },
            bottomBar = {
                Surface(color = Ink, tonalElevation = 8.dp) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (selectedImage != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Image(bitmap = selectedImage!!, contentDescription = "تصویر انتخاب‌شده", modifier = Modifier.size(64.dp), contentScale = ContentScale.Crop)
                                Text("تصویر آماده ارسال است", color = Muted, style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = { selectedImage = null; selectedImageBase64 = null }) { Text("حذف", color = Gold) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy) { Text("＋ عکس", color = Gold) }
                            TextButton(onClick = { imageMode = !imageMode; if (imageMode) { selectedImage = null; selectedImageBase64 = null } }, enabled = !busy) {
                                Text(if (imageMode) "✦ حالت ساخت تصویر روشن" else "✦ ساخت تصویر", color = if (imageMode) Color(0xFFFFD58A) else Gold)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (conversationMode) {
                                        conversationMode = false
                                        listening = false
                                        mediaPlayer?.runCatching { stop() }; mediaPlayer?.release(); mediaPlayer = null
                                    } else {
                                        conversationMode = true
                                        startVoiceCapture?.invoke()
                                    }
                                },
                                enabled = !busy || conversationMode,
                                modifier = Modifier.size(48.dp).background(if (conversationMode || listening) Gold else Panel, CircleShape)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = if (conversationMode) "پایان مکالمه صوتی" else "شروع مکالمه صوتی", tint = if (conversationMode || listening) Ink else Gold)
                            }
                            OutlinedTextField(
                                value = prompt, onValueChange = { prompt = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (imageMode) "توضیح تصویری که می‌خواهی بسازم…" else if (conversationMode) "حالت مکالمه صوتی فعاله…" else "پیامت رو بنویس…", color = Muted) },
                                maxLines = 4, shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF3A3D45),
                                    focusedContainerColor = Panel, unfocusedContainerColor = Panel
                                )
                            )
                            IconButton(
                                onClick = { sendMessage(false) },
                                enabled = !busy && (prompt.isNotBlank() || !selectedImageBase64.isNullOrBlank()),
                                modifier = Modifier.size(48.dp).background(if (!busy && (prompt.isNotBlank() || !selectedImageBase64.isNullOrBlank())) Gold else Panel, CircleShape)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = if (imageMode) "ساخت تصویر" else "ارسال", tint = if (!busy && (prompt.isNotBlank() || !selectedImageBase64.isNullOrBlank())) Ink else Muted)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                if (messages.isEmpty()) {
                    Spacer(Modifier.height(34.dp))
                    Text("سلام نعیم 👋", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(8.dp))
                    Text("من آماده‌ام. بنویس یا برای مکالمه صوتی، میکروفون رو بزن و صحبت کن. بعد از هر پاسخ دوباره گوش می‌دم.", color = Muted, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(onClick = { prompt = "امروز چه کاری انجام بدم؟" }, label = { Text("برنامه‌ریزی روز") })
                        SuggestionChip(onClick = { prompt = "یک ایده خلاقانه بهم بده" }, label = { Text("ایده‌پردازی") })
                    }
                }
                if (error.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2425)), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(error, color = Color(0xFFFFC4C4), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold, trackColor = Panel)
                }
                LazyColumn(
                    state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.88f),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = if (msg.fromUser) Color(0xFF34302A) else Panel)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(if (msg.fromUser) "شما" else "Naeem AI", color = Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(5.dp))
                                    Text(msg.text, color = Color(0xFFF2F0EA), style = MaterialTheme.typography.bodyLarge)
                                    msg.image?.let { image ->
                                        Spacer(Modifier.height(8.dp))
                                        Image(bitmap = image, contentDescription = "تصویر گفتگو", modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit)
                                    }
                                    if (!msg.fromUser && msg.image == null) {
                                        TextButton(onClick = { playAnswer(msg.text) }) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Gold)
                                            Spacer(Modifier.width(5.dp))
                                            Text("پخش پاسخ", color = Gold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = Panel,
                title = { Text("تنظیمات اتصال", color = Gold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("نشانی سرویس (Base URL)")
                        OutlinedTextField(baseUrl, { baseUrl = it }, singleLine = true)
                        Text("نام مدل گفتگو")
                        OutlinedTextField(model, { model = it }, singleLine = true)
                        Text("مدل ساخت تصویر در GapGPT")
                        OutlinedTextField(imageModel, { imageModel = it }, singleLine = true)
                        Text("کلید API")
                        OutlinedTextField(apiKey, { apiKey = it }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Text("کلید به‌صورت رمزگذاری‌شده روی همین گوشی ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        store.save(baseUrl.trim(), model.trim(), apiKey.trim())
                        store.saveImageModel(imageModel)
                        showSettings = false
                    }) { Text("ذخیره", color = Gold) }
                },
                dismissButton = { TextButton(onClick = { showSettings = false }) { Text("انصراف") } }
            )
        }
    }
}
