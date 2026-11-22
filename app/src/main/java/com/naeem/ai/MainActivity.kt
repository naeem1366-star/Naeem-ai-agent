package com.naeem.ai

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatMessage(val text: String, val fromUser: Boolean)

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
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val client = remember { ChatClient() }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale("fa", "IR")
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
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
                    if (ttsReady) tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "naeem-answer")
                } catch (e: Exception) {
                    error = e.message ?: "خطای نامشخص"
                } finally { busy = false }
            }
        }
    }

    fun sendMessage(speakAnswer: Boolean = false) {
        val question = prompt.trim()
        if (question.isBlank() || busy) return
        prompt = ""
        messages.add(ChatMessage(question, true))
        busy = true
        error = ""
        scope.launch {
            try {
                val answer = client.send(baseUrl, model, apiKey, question)
                messages.add(ChatMessage(answer, false))
                if (speakAnswer && ttsReady) tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "naeem-answer")
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
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "صحبت کن…")
                                }
                                listening = true
                                try { speechLauncher.launch(intent) } catch (_: Exception) { listening = false; error = "سرویس تشخیص گفتار روی گوشی در دسترس نیست." }
                            },
                            enabled = !busy,
                            modifier = Modifier.size(48.dp).background(if (listening) Gold else Panel, CircleShape)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "گفت‌وگوی صوتی", tint = if (listening) Ink else Gold)
                        }
                        OutlinedTextField(
                            value = prompt, onValueChange = { prompt = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("پیامت رو بنویس…", color = Muted) },
                            maxLines = 4, shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF3A3D45),
                                focusedContainerColor = Panel, unfocusedContainerColor = Panel
                            )
                        )
                        IconButton(
                            onClick = { sendMessage(false) },
                            enabled = !busy && prompt.isNotBlank(),
                            modifier = Modifier.size(48.dp).background(if (!busy && prompt.isNotBlank()) Gold else Panel, CircleShape)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "ارسال", tint = if (!busy && prompt.isNotBlank()) Ink else Muted)
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
                    Text("من آماده‌ام. بنویس یا دکمه میکروفون رو بزن و صحبت کن.", color = Muted, style = MaterialTheme.typography.bodyLarge)
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
                                    if (!msg.fromUser) {
                                        TextButton(onClick = { if (ttsReady) tts?.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, "naeem-replay") }) {
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
                        Text("نام مدل")
                        OutlinedTextField(model, { model = it }, singleLine = true)
                        Text("کلید API")
                        OutlinedTextField(apiKey, { apiKey = it }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Text("کلید به‌صورت رمزگذاری‌شده روی همین گوشی ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        store.save(baseUrl.trim(), model.trim(), apiKey.trim())
                        showSettings = false
                    }) { Text("ذخیره", color = Gold) }
                },
                dismissButton = { TextButton(onClick = { showSettings = false }) { Text("انصراف") } }
            )
        }
    }
}
