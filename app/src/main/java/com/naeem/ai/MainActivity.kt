package com.naeem.ai

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.os.Bundle
import android.media.MediaPlayer
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Typography
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import com.naeem.ai.core.assistant.*
import java.io.File

private val NaeemPersianFont = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private fun naeemTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = NaeemPersianFont),
        displayMedium = base.displayMedium.copy(fontFamily = NaeemPersianFont),
        displaySmall = base.displaySmall.copy(fontFamily = NaeemPersianFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = NaeemPersianFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = NaeemPersianFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = NaeemPersianFont),
        titleLarge = base.titleLarge.copy(fontFamily = NaeemPersianFont),
        titleMedium = base.titleMedium.copy(fontFamily = NaeemPersianFont),
        titleSmall = base.titleSmall.copy(fontFamily = NaeemPersianFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = NaeemPersianFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = NaeemPersianFont),
        bodySmall = base.bodySmall.copy(fontFamily = NaeemPersianFont),
        labelLarge = base.labelLarge.copy(fontFamily = NaeemPersianFont),
        labelMedium = base.labelMedium.copy(fontFamily = NaeemPersianFont),
        labelSmall = base.labelSmall.copy(fontFamily = NaeemPersianFont)
    )
}

private fun isImageGenerationRequest(text: String): Boolean {
    val normalized = text.lowercase().replace('ي', 'ی').replace('ك', 'ک')
    val imageWords = "(?:تصویر|عکس|عکسی|image|picture|photo)"
    val generationVerbs = "(?:بساز|بسازید|تولید\\s*کن|ایجاد\\s*کن|خلق\\s*کن|generate|create|make)"
    return Regex("$imageWords.{0,45}$generationVerbs|$generationVerbs.{0,35}$imageWords")
        .containsMatchIn(normalized)
}

data class ChatMessage(val text: String, val fromUser: Boolean, val image: androidx.compose.ui.graphics.ImageBitmap? = null)

class MainActivity : FragmentActivity() {
    private val unlocked = mutableStateOf(false)
    private val introCompleted = mutableStateOf(false)
    private var promptShowing = false
    private var requireAuthentication = true
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                promptShowing = false
                requireAuthentication = false
                unlocked.value = true
            }
            override fun onAuthenticationFailed() { }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                promptShowing = false
                unlocked.value = false
                requireAuthentication = true
            }
        })
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("قفل Naeem AI")
            .setSubtitle("برای ورود اثر انگشت یا قفل صفحه گوشی را تأیید کن")
            .setAllowedAuthenticators(authenticators)
            .build()
        val store = SecureConfigStore(this)
        introCompleted.value = false
        setContent {
            if (unlocked.value) {
                if (introCompleted.value) NaeemApp(store)
                else AppIntegrationIntro(onEnter = {
                    introCompleted.value = true
                })
            }
            else AppLockScreen(
                onUnlock = { authenticate() },
                message = if (BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS)
                    "برای ورود، هویتت را تأیید کن"
                else "برای استفاده از قفل، ابتدا اثر انگشت یا قفل صفحه را در تنظیمات گوشی فعال کن."
            )
        }
        window.decorView.post { authenticate() }
    }

    override fun onStop() {
        super.onStop()
        if (unlocked.value) {
            unlocked.value = false
            introCompleted.value = false
            requireAuthentication = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (::biometricPrompt.isInitialized && requireAuthentication && !promptShowing && !unlocked.value) {
            window.decorView.post { authenticate() }
        }
    }

    private fun authenticate() {
        if (isFinishing || isDestroyed || promptShowing || unlocked.value) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            requireAuthentication = true
            return
        }
        promptShowing = true
        try { biometricPrompt.authenticate(promptInfo) }
        catch (_: Exception) { promptShowing = false }
    }
}

@Composable
private fun AppLockScreen(onUnlock: () -> Unit, message: String) {
    val silver = Color(0xFFD5D9DE)
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF08090B)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(112.dp).background(Color(0xFF202329), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "اثر انگشت", tint = silver, modifier = Modifier.size(64.dp))
                }
                Text("Naeem AI", color = silver, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("فضای شخصی تو", color = Color(0xFF9299A3), style = MaterialTheme.typography.bodyMedium)
                Text(message, color = Color(0xFF9299A3), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onUnlock, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF343A42))) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("باز کردن برنامه", color = silver)
                }
            }
        }
    }
}

@Composable
private fun AppIntegrationIntro(onEnter: () -> Unit) {
    val silver = Color(0xFFE0E5EC)
    val muted = Color(0xFFB0BAC6)
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080A0D)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            ThinkingOrb(diameter = 168.dp, active = true)
            Text("فراتر از یک دستیار گفت‌وگومحور", color = silver, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Surface(
                color = Color(0xFF171C22),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF343D48)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text(
                        "Naeem AI قرار نیست فقط به سؤال‌ها جواب بدهد؛ قرار است منظور تو را بفهمد و آن را به یک کار واقعی تبدیل کند. می‌توانی طبیعی حرف بزنی؛ از خرید و هزینه‌ها، تمرین امروز، برنامه سفر یا چیزی که می‌خواهی به خاطر بسپاری. دستیار از متن گفتگو، موضوع و اطلاعات مرتبط را استخراج می‌کند، تشخیص می‌دهد هر موضوع به کدام بخش تعلق دارد و پیشنهاد می‌دهد قدم بعدی چه باشد. اگر جزئیات کافی نباشد، به‌جای حدس زدن سؤال می‌پرسد؛ اگر قرار باشد چیزی ثبت یا تغییر کند، اول نتیجه را شفاف به تو نشان می‌دهد و فقط با تأیید تو اقدام می‌کند. گفتگو، حافظه و ابزارهای تخصصی در کنار هم کار می‌کنند، اما اطلاعات هر بخش در جای خودش می‌ماند و کنترل نهایی همیشه دست توست. تفاوت Naeem AI در همین پیوند است: هوش مصنوعی‌ای که فقط حرف نمی‌زند؛ می‌فهمد، هماهنگ می‌کند و با اجازه تو عمل می‌کند.",
                        color = muted,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.32
                    )
                }
            }
            Text(
                "یک هسته هوشمند؛ چندین فضای مستقل؛ یک تجربه پیوسته.",
                color = Color(0xFF8F9AA7),
                style = MaterialTheme.typography.labelMedium
            )
            Button(
                onClick = onEnter,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = silver, contentColor = Color(0xFF101318)),
                shape = RoundedCornerShape(17.dp)
            ) { Text("ورود به فضای من", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val Ink = Color(0xFF080A0D)
private val Panel = Color(0xFF191D22)
private val Gold = Color(0xFFD4D9E0)
private val Muted = Color(0xFF929BA6)


@Composable
private fun HomeShortcut(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.height(112.dp).clickable { onClick() }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202329)), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.19f), Color(0xFF202329)))).padding(13.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
                }
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = Color(0xFFADB3BA), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

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
    var providerTestStatus by remember { mutableStateOf("") }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var testingProvider by remember { mutableStateOf(false) }
    val conversationStore = remember { ConversationStore(context) }
    var activeConversationId by remember { mutableStateOf(conversationStore.activeId()) }
    var conversationReload by remember { mutableStateOf(0) }
    val messages = remember(activeConversationId, conversationReload) {
        mutableStateListOf<ChatMessage>().apply {
            addAll(conversationStore.load(activeConversationId).map { ChatMessage(it.text, it.fromUser) })
        }
    }
    var showConversationManager by remember { mutableStateOf(false) }
    var conversationSearch by remember { mutableStateOf("") }
    var showArchivedConversations by remember { mutableStateOf(false) }
    var conversationRefresh by remember { mutableStateOf(0) }
    var renameConversationId by remember { mutableStateOf<String?>(null) }
    var deleteConversationTarget by remember { mutableStateOf<StoredConversation?>(null) }
    var renameConversationDraft by remember { mutableStateOf("") }
    val dataStore = remember { AssistantDataStore(context) }
    val deepSearchClient = remember { DeepSearchClient() }
    var memories by remember { mutableStateOf(dataStore.memories()) }
    var albumImages by remember { mutableStateOf(dataStore.images()) }
    val ledgerStore = remember { LedgerStore(context) }
    var ledgerEntries by remember { mutableStateOf(ledgerStore.entries()) }
    val assistantRouter = remember {
        AssistantActionRouter(
            ToolRegistry(
                listOf(
                    FinanceAssistantTool(ledgerStore),
                    SportsAssistantTool(context.applicationContext),
                    TravelAssistantTool(context.applicationContext),
                    MemoryAssistantTool(dataStore)
                )
            )
        )
    }
    var pendingAssistantAction by remember { mutableStateOf<AssistantAction?>(null) }
    var showFinance by remember { mutableStateOf(false) }
    var showHub by remember { mutableStateOf(false) }
    var showHealth by remember { mutableStateOf(false) }
    var showGames by remember { mutableStateOf(false) }
    var showDiscover by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(false) }
    var showTravel by remember { mutableStateOf(false) }
    var showSports by remember { mutableStateOf(false) }
    var showTrail by remember { mutableStateOf(false) }
    var showMarkets by remember { mutableStateOf(false) }
    var showWrapped by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var showAlbum by remember { mutableStateOf(false) }
    var memoryDraft by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var speechPaused by remember { mutableStateOf(false) }
    var speechJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val backupManager = remember {
        NaeemBackupManager(ledgerStore, SportStore(context), TravelStore(context), dataStore, conversationStore)
    }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val password = backupPassphrase
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) { backupManager.exportEncrypted(password) }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("ساخت فایل پشتیبان ممکن نشد.")
                    }
                    backupStatus = "پشتیبان رمزگذاری‌شده با موفقیت ذخیره شد."
                } catch (e: Exception) {
                    backupStatus = e.message ?: "ساخت پشتیبان ناموفق بود."
                }
            }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val password = backupPassphrase
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: throw IllegalStateException("خواندن فایل پشتیبان ممکن نشد.")
                    }
                    val summary = withContext(Dispatchers.IO) { backupManager.restoreEncrypted(json, password) }
                    ledgerEntries = ledgerStore.entries()
                    memories = dataStore.memories()
                    albumImages = dataStore.images()
                    activeConversationId = conversationStore.activeId()
                    conversationReload++
                    pendingAssistantAction = null
                    backupStatus = "بازیابی انجام شد: " + summary.financeEntries + " تراکنش، " +
                        summary.workouts + " تمرین، " + summary.trips + " سفر، " + summary.memories +
                        " یادداشت، " + summary.conversations + " گفتگو و " + summary.images + " تصویر."
                } catch (e: Exception) {
                    backupStatus = e.message ?: "بازیابی پشتیبان ناموفق بود."
                }
            }
        }
    }
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
    var speechPlaying by remember { mutableStateOf(false) }
    var conversationMode by remember { mutableStateOf(false) }
    val currentConversationMode by rememberUpdatedState(conversationMode)
    var startVoiceCapture by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun stopSpeech() {
        speechJob?.cancel()
        speechJob = null
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
        speechPlaying = false
        speechPaused = false
    }

    fun playAnswer(text: String, continueListening: Boolean = false) {
        stopSpeech()
        speechJob = scope.launch {
            speechPlaying = true
            speechPaused = false
            try {
                val textChunks = audioClient.splitText(text)
                for (textChunk in textChunks) {
                    if (!isActive) break
                    val audioBytes = audioClient.synthesize(baseUrl, apiKey, textChunk)
                    val generatedFile = File(context.cacheDir, "naeem_tts_${System.currentTimeMillis()}.mp3")
                    generatedFile.writeBytes(audioBytes)
                    val player = MediaPlayer()
                    mediaPlayer = player
                    suspendCancellableCoroutine<Unit> { continuation ->
                        player.setDataSource(generatedFile.absolutePath)
                        player.setOnCompletionListener { completed ->
                            if (mediaPlayer === completed) mediaPlayer = null
                            completed.release()
                            generatedFile.delete()
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        player.setOnErrorListener { failed, _, _ ->
                            if (mediaPlayer === failed) mediaPlayer = null
                            failed.release()
                            generatedFile.delete()
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("پخش یکی از بخش‌های صدای فارسی ناموفق بود."))
                            true
                        }
                        player.setOnPreparedListener { prepared -> if (continuation.isActive) prepared.start() }
                        continuation.invokeOnCancellation {
                            runCatching { player.release() }
                            generatedFile.delete()
                            if (mediaPlayer === player) mediaPlayer = null
                        }
                        player.prepareAsync()
                    }
                }
                if (continueListening && currentConversationMode && !busy) startVoiceCapture?.invoke()
            } catch (e: Exception) {
                if (isActive) error = e.message ?: "ساخت یا پخش صدای فارسی ناموفق بود."
            } finally {
                mediaPlayer?.runCatching { release() }
                mediaPlayer = null
                speechPlaying = false
                speechPaused = false
                speechJob = null
            }
        }
    }

    fun pauseOrResumeSpeech() {
        val player = mediaPlayer ?: return
        runCatching {
            if (speechPaused) { player.start(); speechPaused = false }
            else if (player.isPlaying) { player.pause(); speechPaused = true }
        }
    }


    suspend fun routeAssistantIntent(question: String): String? {
        return when (val resolution = PersianAssistantIntentResolver.resolve(question, TravelStore(context).load())) {
            AssistantIntentResolution.NoMatch -> null
            is AssistantIntentResolution.Clarify -> resolution.question
            is AssistantIntentResolution.Ready -> when (val routed = assistantRouter.route(resolution.action)) {
                is ActionRouteResult.Completed -> {
                    ledgerEntries = ledgerStore.entries()
                    memories = dataStore.memories()
                    routed.result.message
                }
                is ActionRouteResult.ConfirmationRequired -> {
                    pendingAssistantAction = routed.action
                    assistantActionPreview(routed.action)
                }
                is ActionRouteResult.ClarificationRequired -> routed.question
                is ActionRouteResult.Rejected -> routed.reason
                is ActionRouteResult.Failed -> routed.reason
            }
        }
    }

    fun confirmPendingAssistantAction() {
        val action = pendingAssistantAction ?: return
        scope.launch {
            when (val result = assistantRouter.confirm(action.id)) {
                is ActionRouteResult.Completed -> {
                    ledgerEntries = ledgerStore.entries()
                    memories = dataStore.memories()
                    messages.add(ChatMessage(result.result.message, false))
                }
                is ActionRouteResult.ClarificationRequired -> messages.add(ChatMessage(result.question, false))
                is ActionRouteResult.Rejected -> messages.add(ChatMessage(result.reason, false))
                is ActionRouteResult.Failed -> messages.add(ChatMessage(result.reason, false))
                is ActionRouteResult.ConfirmationRequired -> Unit
            }
            pendingAssistantAction = null
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
                    val actionReply = routeAssistantIntent(question)
                    if (actionReply != null) {
                        messages.add(ChatMessage(actionReply, false))
                        playAnswer(actionReply, continueListening = true)
                        return@launch
                    }
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
        val shouldGenerateImage = imageMode || isImageGenerationRequest(finalQuestion)
        prompt = ""
        selectedImageBase64 = null
        selectedImage = null
        messages.add(ChatMessage(if (shouldGenerateImage) "ساخت تصویر: $finalQuestion" else finalQuestion, true, imagePreview))
        busy = true
        error = ""
        scope.launch {
            try {
                if (shouldGenerateImage) {
                    val bitmap = imageClient.generate(baseUrl, apiKey, imageModel, finalQuestion)
                    dataStore.saveImage(bitmap, finalQuestion)
                    albumImages = dataStore.images()
                    messages.add(ChatMessage("تصویر ساخته شد و در آلبوم ذخیره شد.", false, bitmap.asImageBitmap()))
                } else {
                    val actionReply = routeAssistantIntent(finalQuestion)
                    if (actionReply != null) {
                        messages.add(ChatMessage(actionReply, false))
                        if (speakAnswer) playAnswer(actionReply)
                        return@launch
                    }
                    val memoryContext = memories.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- $it" }?.let { "\n\nاطلاعاتی که کاربر قبلاً برای یادآوری ذخیره کرده:\n$it" }.orEmpty()
                    val answer = if (searchMode) {
                        val findings = deepSearchClient.search(finalQuestion, 8)
                        val sourceText = findings.mapIndexed { index, item -> "[$index] ${item.title}\n${item.snippet}\nURL: ${item.url}" }.joinToString("\n\n")
                        val researchPrompt = "برای پرسش زیر یک گزارش پژوهشی دقیق و فارسی بنویس. از منابع زیر استفاده کن، ادعاها را به شماره منبع ارجاع بده، اختلاف اطلاعات را مشخص کن و در پایان فهرست منابع را با URL کامل بیاور. اگر منابع کافی نیستند صادقانه بگو.\n\nپرسش: $finalQuestion\n\nمنابع وب:\n$sourceText$memoryContext"
                        val report = client.send(baseUrl, model, apiKey, researchPrompt)
                        report + "\n\nمنابع بررسی‌شده:\n" + findings.mapIndexed { index, item -> "[${index + 1}] ${item.title} — ${item.url}" }.joinToString("\n")
                    } else {
                        val memoryAwarePrompt = finalQuestion + memoryContext
                        if (finalQuestion.contains("به خاطر بسپار") || finalQuestion.contains("یادت باشه")) {
                            val note = finalQuestion.replace("به خاطر بسپار", "").replace("یادت باشه", "").trim()
                            if (note.isNotBlank()) { dataStore.addMemory(note); memories = dataStore.memories() }
                        }
                        client.send(baseUrl, model, apiKey, memoryAwarePrompt, imageToSend)
                    }
                    messages.add(ChatMessage(answer, false))
                    if (speakAnswer) playAnswer(answer)
                }
            } catch (e: Exception) {
                error = e.message ?: "خطای نامشخص"
            } finally { busy = false }
        }
    }

    LaunchedEffect(activeConversationId, messages.size) {
        conversationStore.save(activeConversationId, messages)
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    DisposableEffect(conversationStore, messages, activeConversationId) {
        onDispose { conversationStore.save(activeConversationId, messages) }
    }

    MaterialTheme(
        typography = naeemTypography(),
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Ink, background = Ink, onBackground = Color(0xFFE7ECF2),
            surface = Panel, onSurface = Color(0xFFE7ECF2), surfaceVariant = Color(0xFF292C33),
            onSurfaceVariant = Color(0xFFBFC6D0), secondary = Color(0xFFBFC6D0)
        )
    ) {
        Scaffold(
            containerColor = Ink,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_brain_mark),
                                contentDescription = "نشان مغز هوشمند Naeem AI",
                                modifier = Modifier.size(28.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("Naeem AI", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text("دستیار هوشمند شخصی", style = MaterialTheme.typography.labelSmall, color = Muted)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { showHub = true }) { Icon(Icons.Default.Menu, contentDescription = "همه قابلیت‌ها", tint = Gold) }
                    },
                    actions = {
                        var showQuickMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = {
                            conversationStore.save(activeConversationId, messages)
                            activeConversationId = conversationStore.create()
                            conversationReload++
                            pendingAssistantAction = null
                            error = ""
                            prompt = ""
                            selectedImage = null
                            selectedImageBase64 = null
                            imageMode = false
                            conversationMode = false
                            stopSpeech()
                        }) {
                            Icon(Icons.Default.AddComment, contentDescription = "گفت‌وگوی تازه", tint = Gold)
                        }
                        Box {
                            IconButton(onClick = { showQuickMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "دسترسی‌های بیشتر", tint = Gold)
                            }
                            DropdownMenu(expanded = showQuickMenu, onDismissRequest = { showQuickMenu = false }, containerColor = Panel) {
                                DropdownMenuItem(text = { Text("گفت‌وگوهای من") }, leadingIcon = { Icon(Icons.Default.Forum, null, tint = Gold) }, onClick = { showQuickMenu = false; showConversationManager = true; conversationRefresh++ })
                                DropdownMenuItem(text = { Text("آلبوم تصاویر") }, leadingIcon = { Icon(Icons.Default.Collections, null, tint = Gold) }, onClick = { showQuickMenu = false; showAlbum = true; albumImages = dataStore.images() })
                                DropdownMenuItem(text = { Text("دفتر مالی") }, leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, tint = Gold) }, onClick = { showQuickMenu = false; showFinance = true; ledgerEntries = ledgerStore.entries() })
                                DropdownMenuItem(text = { Text("حافظه هوشمند") }, leadingIcon = { Icon(Icons.Default.Bookmark, null, tint = Gold) }, onClick = { showQuickMenu = false; showMemory = true; memories = dataStore.memories() })
                                DropdownMenuItem(text = { Text("تنظیمات") }, leadingIcon = { Icon(Icons.Default.Settings, null, tint = Gold) }, onClick = { showQuickMenu = false; showSettings = true })
                            }
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
                                Text(if (imageMode) "✦ ساخت تصویر روشن" else "✦ ساخت تصویر", color = if (imageMode) Color(0xFFD4D9E0) else Gold)
                            }
                            TextButton(onClick = { searchMode = !searchMode }, enabled = !busy) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = if (searchMode) Color(0xFFD4D9E0) else Gold)
                                Text(if (searchMode) "جست‌وجوی عمیق روشن" else "جست‌وجوی عمیق", color = if (searchMode) Color(0xFFD4D9E0) else Gold)
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
                    Spacer(Modifier.height(28.dp))
                    Column(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("NAEEM AI", color = Gold.copy(alpha = 0.78f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        ThinkingOrb(diameter = 142.dp, active = false)
                        Spacer(Modifier.height(15.dp))
                        Text("سلام نعیم", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("آماده‌ام؛ از هرجا دوست داری شروع کنیم.", color = Color(0xFFBFC6D0), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(22.dp))
                        OutlinedButton(
                            onClick = { showHub = true },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.65f)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.GridView, null, tint = Gold)
                            Spacer(Modifier.width(8.dp))
                            Text("مرور فضاها", color = Gold)
                        }
                    }
                }
                if (error.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2425)), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(error, color = Color(0xFFFFC4C4), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (conversationMode || listening || speechPlaying || busy) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF191D22))
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color(0xFF30363E), Color(0xFF1A1A1C))))
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ThinkingOrb(diameter = 158.dp, active = true)
                            Text(
                                when {
                                    speechPlaying -> "در حال صحبت با تو"
                                    listening -> "در حال شنیدن صدای تو"
                                    searchMode && busy -> "در حال جست‌وجو و بررسی منابع…"
                                    imageMode && busy -> "در حال ساخت تصویر…"
                                    busy -> "دارم فکر می‌کنم…"
                                    else -> "حالت مکالمه فعال است"
                                },
                                color = Gold, fontWeight = FontWeight.SemiBold
                            )
                            Text("گوی هوشمند Naeem AI", color = Muted, style = MaterialTheme.typography.labelSmall)
                            if (busy) {
                                LinearProgressIndicator(
                                    Modifier.fillMaxWidth().padding(top = 5.dp),
                                    color = Gold, trackColor = Panel
                                )
                            }
                        }
                    }
                }
                pendingAssistantAction?.let { action ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF30363E)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("تأیید قبل از ثبت", color = Gold, fontWeight = FontWeight.Bold)
                            Text(assistantActionPreview(action), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text("هیچ تغییری تا زمان تأیید تو در اطلاعات برنامه انجام نمی‌شود.", color = Muted, style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { confirmPendingAssistantAction() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                                    modifier = Modifier.weight(1f)
                                ) { Text("تأیید و ثبت") }
                                OutlinedButton(
                                    onClick = {
                                        assistantRouter.cancel(action.id)
                                        pendingAssistantAction = null
                                        messages.add(ChatMessage("عملیات لغو شد و چیزی ثبت نشد.", false))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("لغو", color = Color.White) }
                            }
                        }
                    }
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
                                colors = CardDefaults.cardColors(containerColor = if (msg.fromUser) Color(0xFF30363E) else Panel)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(if (msg.fromUser) "شما" else "Naeem AI", color = Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(5.dp))
                                    Text(msg.text, color = Color(0xFFE7ECF2), style = MaterialTheme.typography.bodyLarge)
                                    msg.image?.let { image ->
                                        Spacer(Modifier.height(8.dp))
                                        Image(bitmap = image, contentDescription = "تصویر گفتگو", modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit)
                                    }
                                    if (!msg.fromUser && msg.image == null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = { playAnswer(msg.text) }) {
                                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Gold)
                                                Spacer(Modifier.width(5.dp))
                                                Text("پخش کامل", color = Gold)
                                            }
                                            if (speechPlaying) {
                                                IconButton(onClick = { pauseOrResumeSpeech() }) {
                                                    Icon(if (speechPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (speechPaused) "ادامه صدا" else "مکث صدا", tint = Gold)
                                                }
                                                IconButton(onClick = { stopSpeech() }) {
                                                    Icon(Icons.Default.Stop, contentDescription = "توقف صدا", tint = Color(0xFFFF8B80))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showHub) {
            Dialog(onDismissRequest = { showHub = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize()) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    FeatureHub(
                        onClose = { showHub = false },
                        onFinance = { showHub = false; showFinance = true; ledgerEntries = ledgerStore.entries() },
                        onHealth = { showHub = false; showHealth = true },
                        onGames = { showHub = false; showGames = true },
                        onDiscover = { showHub = false; showDiscover = true },
                        onMusic = { showHub = false; showMusic = true },
                        onMemory = { showHub = false; showMemory = true; memories = dataStore.memories() },
                        onAlbum = { showHub = false; showAlbum = true; albumImages = dataStore.images() },
                        onSettings = { showHub = false; showSettings = true },
                        onTravel = { showHub = false; showTravel = true },
                        onSport = { showHub = false; showSports = true },
                        onMarkets = { showHub = false; showMarkets = true },
                        onWrapped = { showHub = false; showWrapped = true },
                        onTrail = { showHub = false; showTrail = true }
                    )
                }
                }
            }
        }
        if (showTravel) {
            Dialog(onDismissRequest = { showTravel = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    TravelDashboard(onClose = { showTravel = false })
                }
            }
        }
        if (showTrail) {
            Dialog(onDismissRequest = { showTrail = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    TrailNavigationDashboard(onClose = { showTrail = false })
                }
            }
        }
        if (showWrapped) {
            Dialog(onDismissRequest = { showWrapped = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    MonthlyWrappedDashboard(onClose = { showWrapped = false })
                }
            }
        }
        if (showMarkets) {
            Dialog(onDismissRequest = { showMarkets = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    MarketsDashboard(onClose = { showMarkets = false })
                }
            }
        }
        if (showSports) {
            Dialog(onDismissRequest = { showSports = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    SportsDashboard(onClose = { showSports = false })
                }
            }
        }
        if (showHealth) {
            Dialog(onDismissRequest = { showHealth = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    HealthDashboard(onClose = { showHealth = false })
                }
            }
        }
        if (showGames) {
            Dialog(onDismissRequest = { showGames = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    GamesHub(onClose = { showGames = false })
                }
            }
        }
        if (showDiscover) {
            Dialog(onDismissRequest = { showDiscover = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    DiscoverDashboard(onClose = { showDiscover = false }, onAskAi = { question -> showDiscover = false; prompt = question })
                }
            }
        }
        if (showMusic) {
            Dialog(onDismissRequest = { showMusic = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    MusicDashboard(onClose = { showMusic = false }, onAskAi = { question -> showMusic = false; prompt = question })
                }
            }
        }
        if (showFinance) {
            Dialog(onDismissRequest = { showFinance = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), color = Ink) {
                    FinanceDashboard(
                        entries = ledgerEntries,
                        onAdd = { amount, category, note, income -> ledgerStore.add(amount, category, note, income); ledgerEntries = ledgerStore.entries() },
                        onDelete = { id -> ledgerStore.delete(id); ledgerEntries = ledgerStore.entries() },
                        onClose = { showFinance = false }
                    )
                }
            }
        }

        if (showMemory) {
            AlertDialog(
                onDismissRequest = { showMemory = false },
                containerColor = Panel,
                title = { Text("حافظه Naeem AI", color = Gold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("نکته‌هایی که اینجا ذخیره می‌کنی، در گفت‌وگوهای بعدی برای پاسخ بهتر استفاده می‌شوند.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = memoryDraft, onValueChange = { memoryDraft = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("مثلاً ترجیح می‌دهم پاسخ‌ها فارسی باشند") })
                        memories.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item, modifier = Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { dataStore.deleteMemory(item); memories = dataStore.memories() }) { Text("حذف", color = Color(0xFFFF8B80)) }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (memoryDraft.isNotBlank()) { dataStore.addMemory(memoryDraft); memories = dataStore.memories(); memoryDraft = "" }
                    }) { Text("ذخیره نکته", color = Gold) }
                },
                dismissButton = { TextButton(onClick = { showMemory = false }) { Text("بستن", color = Muted) } }
            )
        }

        if (showAlbum) {
            AlertDialog(
                onDismissRequest = { showAlbum = false },
                containerColor = Panel,
                title = { Text("آلبوم تصاویر ساخته‌شده", color = Gold) },
                text = {
                    if (albumImages.isEmpty()) {
                        Text("هنوز تصویری نساخته‌ای. تصاویر بعدی به‌صورت خودکار اینجا ذخیره می‌شوند.", color = Muted)
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            gridItems(albumImages) { item ->
                                val bitmap = remember(item.file.absolutePath) { BitmapFactory.decodeFile(item.file.absolutePath)?.asImageBitmap() }
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF292C33))) {
                                    Column {
                                        bitmap?.let { Image(bitmap = it, contentDescription = item.prompt, modifier = Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop) }
                                        Text(item.prompt, color = Color.White, maxLines = 2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
                                        TextButton(onClick = { dataStore.deleteImage(item); albumImages = dataStore.images() }) { Text("حذف از آلبوم", color = Color(0xFFFF8B80)) }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAlbum = false }) { Text("بستن", color = Gold) } }
            )
        }

        if (showConversationManager) {
            val visibleThreads = remember(conversationSearch, showArchivedConversations, conversationRefresh) {
                conversationStore.search(conversationSearch, showArchivedConversations)
            }
            AlertDialog(
                onDismissRequest = { showConversationManager = false },
                containerColor = Panel,
                title = { Text("گفت‌وگوهای من", color = Gold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedTextField(
                            value = conversationSearch,
                            onValueChange = { conversationSearch = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("جست‌وجو در عنوان و متن گفتگوها") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    activeConversationId = conversationStore.create()
                                    showConversationManager = false
                                    pendingAssistantAction = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
                            ) { Text("＋ گفت‌وگوی تازه") }
                            TextButton(onClick = { showArchivedConversations = !showArchivedConversations; conversationRefresh++ }) {
                                Text(if (showArchivedConversations) "نمایش فعال‌ها" else "بایگانی‌شده‌ها", color = Gold)
                            }
                        }
                        if (visibleThreads.isEmpty()) {
                            Text("گفت‌وگویی پیدا نشد.", color = Muted)
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                items(visibleThreads, key = { it.id }) { thread ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF292C33))) {
                                        Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(thread.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text(thread.messages.size.toString() + " پیام" + if (thread.id == activeConversationId) " • گفت‌وگوی فعلی" else "", color = Muted, style = MaterialTheme.typography.labelSmall)
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                if (thread.archivedAt == null) {
                                                    TextButton(onClick = {
                                                        conversationStore.switchTo(thread.id)
                                                        activeConversationId = thread.id
                                                        showConversationManager = false
                                                        pendingAssistantAction = null
                                                    }) { Text("بازکردن", color = Gold) }
                                                    TextButton(onClick = {
                                                        renameConversationId = thread.id
                                                        renameConversationDraft = thread.title
                                                    }) { Text("تغییر نام", color = Color.White) }
                                                    TextButton(onClick = {
                                                        conversationStore.archive(thread.id, true)
                                                        activeConversationId = conversationStore.activeId()
                                                        conversationRefresh++
                                                    }) { Text("بایگانی", color = Muted) }
                                                } else {
                                                    TextButton(onClick = {
                                                        conversationStore.archive(thread.id, false)
                                                        conversationRefresh++
                                                    }) { Text("بازگردانی", color = Gold) }
                                                }
                                                TextButton(onClick = { deleteConversationTarget = thread }) { Text("حذف", color = Color(0xFFFF8B80)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showConversationManager = false }) { Text("بستن", color = Gold) } }
            )
        }

        deleteConversationTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteConversationTarget = null },
                containerColor = Panel,
                title = { Text("حذف گفت‌وگو", color = Gold) },
                text = { Text("گفت‌وگوی «" + target.title + "» و تاریخچهٔ آن برای همیشه حذف شود؟", color = Color.White) },
                confirmButton = {
                    TextButton(onClick = {
                        conversationStore.delete(target.id)
                        if (target.id == activeConversationId) activeConversationId = conversationStore.activeId()
                        conversationRefresh++
                        deleteConversationTarget = null
                    }) { Text("حذف", color = Color(0xFFFF8B80)) }
                },
                dismissButton = { TextButton(onClick = { deleteConversationTarget = null }) { Text("انصراف", color = Muted) } }
            )
        }

        if (renameConversationId != null) {
            AlertDialog(
                onDismissRequest = { renameConversationId = null },
                containerColor = Panel,
                title = { Text("تغییر نام گفت‌وگو", color = Gold) },
                text = {
                    OutlinedTextField(
                        value = renameConversationDraft,
                        onValueChange = { renameConversationDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        renameConversationId?.let { conversationStore.rename(it, renameConversationDraft) }
                        renameConversationId = null
                        conversationRefresh++
                    }) { Text("ذخیره", color = Gold) }
                },
                dismissButton = { TextButton(onClick = { renameConversationId = null }) { Text("انصراف", color = Muted) } }
            )
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
                        Button(
                            onClick = {
                                testingProvider = true
                                providerTestStatus = ""
                                scope.launch {
                                    try {
                                        availableModels = client.fetchModels(baseUrl, apiKey)
                                        providerTestStatus = "اتصال موفق؛ " + availableModels.size + " مدل دریافت شد."
                                    } catch (e: Exception) {
                                        providerTestStatus = e.message ?: "آزمایش اتصال ناموفق بود."
                                    } finally {
                                        testingProvider = false
                                    }
                                }
                            },
                            enabled = !testingProvider && apiKey.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (testingProvider) "در حال آزمایش اتصال…" else "آزمایش اتصال و دریافت مدل‌ها")
                        }
                        if (providerTestStatus.isNotBlank()) Text(providerTestStatus, color = if (availableModels.isNotEmpty()) Color(0xFFD4D9E0) else Color(0xFFFFB2A8), style = MaterialTheme.typography.bodySmall)
                        if (availableModels.isNotEmpty()) {
                            Text("برای انتخاب مدل، روی یکی از شناسه‌ها بزن:", color = Muted, style = MaterialTheme.typography.labelSmall)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                availableModels.take(12).forEach { modelId ->
                                    TextButton(onClick = { model = modelId }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                        Text(modelId, color = if (model == modelId) Gold else Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Text("کلید API")
                        OutlinedTextField(apiKey, { apiKey = it }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Text("کلید به‌صورت رمزگذاری‌شده روی همین گوشی ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall, color = Muted)
                        Divider(color = Color(0xFF3A3D45), modifier = Modifier.padding(vertical = 5.dp))
                        Text("پشتیبان‌گیری و بازیابی", color = Gold, fontWeight = FontWeight.SemiBold)
                        Text("از اطلاعات مالی، تمرین‌ها، سفرها، حافظه، گفتگوها و آلبوم تصویر، یک فایل رمزگذاری‌شده می‌سازد. رمز را در جای امن نگه دار؛ بدون آن بازیابی ممکن نیست.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = backupPassphrase,
                            onValueChange = { backupPassphrase = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("رمز پشتیبان (حداقل ۸ نویسه)") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { backupExportLauncher.launch("NaeemAI-Backup.json") },
                                enabled = backupPassphrase.length >= 8,
                                modifier = Modifier.weight(1f)
                            ) { Text("خروجی گرفتن", color = Gold) }
                            OutlinedButton(
                                onClick = { backupImportLauncher.launch(arrayOf("application/json", "text/json")) },
                                enabled = backupPassphrase.length >= 8,
                                modifier = Modifier.weight(1f)
                            ) { Text("بازیابی", color = Gold) }
                        }
                        if (backupStatus.isNotBlank()) Text(backupStatus, color = if (backupStatus.contains("موفق")) Color(0xFFD4D9E0) else Color(0xFFFFB2A8), style = MaterialTheme.typography.bodySmall)
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


@Composable
private fun ThinkingOrb(
    diameter: androidx.compose.ui.unit.Dp = 118.dp,
    active: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "liquid-mercury")
    val phase by transition.animateFloat(
        0f, 6.28318f,
        infiniteRepeatable(tween(if (active) 4200 else 7000, easing = LinearEasing)),
        label = "mercury-flow"
    )
    val secondaryPhase by transition.animateFloat(
        0f, 6.28318f,
        infiniteRepeatable(tween(if (active) 6900 else 9800, easing = LinearEasing)),
        label = "mercury-secondary-flow"
    )
    val pulse by transition.animateFloat(
        0.965f, 1.035f,
        infiniteRepeatable(tween(if (active) 2100 else 3300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mercury-breath"
    )
    val wavePhase by transition.animateFloat(
        0f, 6.28318f,
        infiniteRepeatable(tween(if (active) 5200 else 8200, easing = LinearEasing)),
        label = "mercury-waves"
    )

    Canvas(Modifier.size(diameter)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * 0.355f * pulse
        val glow = base * 1.95f

        // Soft ambient light; deliberately no enclosing halo ring.
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x5596A1AE), Color(0x225B6674), Color.Transparent),
                c, glow
            ), glow, c
        )

        // Two flowing energy ribbons pass behind the mercury sphere.
        val waveWidth = size.width * 0.49f
        val waveStart = c.x - waveWidth / 2f
        val waveEnd = c.x + waveWidth / 2f
        fun ribbonPath(offset: Float, amplitude: Float, speed: Float): Path {
            val path = Path()
            val count = 90
            for (i in 0..count) {
                val x = waveStart + (waveEnd - waveStart) * i / count
                val t = i.toFloat() / count
                val envelope = kotlin.math.sin(t * Math.PI).toFloat().let { it * it }
                val y = c.y + offset +
                    kotlin.math.sin(t * 8.2f + wavePhase * speed) * amplitude * envelope +
                    kotlin.math.sin(t * 15f - wavePhase * speed * 0.7f) * amplitude * 0.18f * envelope
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }
        val orangeRibbon = ribbonPath(base * 0.02f, base * 0.42f, 1f)
        val greenRibbon = ribbonPath(-base * 0.06f, base * 0.31f, -0.78f)
        drawPath(
            orangeRibbon,
            Brush.horizontalGradient(listOf(Color.Transparent, Color(0x00FF9A45), Color(0xFFFF9B45), Color(0xFFFFC17A), Color.Transparent), waveStart, waveEnd),
            style = Stroke(width = 5.2.dp.toPx())
        )
        drawPath(
            orangeRibbon,
            Color(0x99FF9A45),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawPath(
            greenRibbon,
            Brush.horizontalGradient(listOf(Color.Transparent, Color(0x0036CFA0), Color(0xFF39D6A0), Color(0xFF8AF0C9), Color.Transparent), waveStart, waveEnd),
            style = Stroke(width = 4.8.dp.toPx())
        )
        drawPath(
            greenRibbon,
            Color(0x8836CFA0),
            style = Stroke(width = 1.4.dp.toPx())
        )

        // Floating shadow adds separation from the dark background.
        drawOval(
            color = Color(0xFF020305).copy(alpha = 0.72f),
            topLeft = Offset(c.x - base * 0.92f, c.y + base * 0.58f),
            size = androidx.compose.ui.geometry.Size(base * 1.84f, base * 0.42f)
        )

        // Organic, continuously changing liquid-metal silhouette.
        val blob = Path()
        val steps = 160
        for (i in 0..steps) {
            val a = (i.toFloat() / steps) * 6.28318f
            val wave = 1f +
                0.105f * kotlin.math.sin(3f * a + phase) +
                0.052f * kotlin.math.sin(5f * a - secondaryPhase * 1.31f) +
                0.032f * kotlin.math.cos(2f * a + phase * 0.72f) +
                0.018f * kotlin.math.sin(7f * a + secondaryPhase * 0.83f)
            val r = base * wave
            val x = c.x + kotlin.math.cos(a) * r * (1.02f + 0.025f * kotlin.math.sin(phase))
            val y = c.y + kotlin.math.sin(a) * r * (0.94f + 0.03f * kotlin.math.cos(secondaryPhase))
            if (i == 0) blob.moveTo(x, y) else blob.lineTo(x, y)
        }
        blob.close()

        // Deep graphite-to-silver body, not a flat white sphere.
        drawPath(
            blob,
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF353B43), Color(0xFFBFC6CF), Color(0xFF69727D),
                    Color(0xFFE3E7EC), Color(0xFF555E69), Color(0xFF20252C)
                ),
                start = Offset(c.x - base, c.y - base),
                end = Offset(c.x + base, c.y + base)
            )
        )

        // Curved reflections create a rounded, three-dimensional liquid surface.
        drawPath(
            blob,
            Brush.radialGradient(
                colors = listOf(Color(0xFFE9EDF2), Color(0x99D8DEE6), Color(0x337C8794), Color.Transparent),
                center = Offset(c.x - base * 0.34f, c.y - base * 0.48f),
                radius = base * 1.2f
            )
        )
        drawArc(
            Brush.linearGradient(listOf(Color(0xFFF9FBFD), Color(0x668C98A7), Color(0x00FFFFFF)), Offset(c.x - base, c.y - base), Offset(c.x + base, c.y + base)),
            198f + phase * 18f, 74f, false,
            Offset(c.x - base * 0.73f, c.y - base * 0.72f),
            androidx.compose.ui.geometry.Size(base * 1.42f, base * 0.92f),
            style = Stroke(width = 4.2.dp.toPx())
        )
        drawArc(
            Color(0xFF252C35).copy(alpha = 0.72f),
            20f - secondaryPhase * 16f, 112f, false,
            Offset(c.x - base * 0.50f, c.y - base * 0.10f),
            androidx.compose.ui.geometry.Size(base * 1.55f, base * 1.08f),
            style = Stroke(width = 2.1.dp.toPx())
        )
        drawPath(
            blob,
            Brush.linearGradient(
                listOf(Color.Transparent, Color(0xDDECF2F8), Color(0x668B98A8), Color.Transparent),
                Offset(c.x - base * 1.2f, c.y - base),
                Offset(c.x + base * 1.2f, c.y + base)
            ),
            style = Stroke(width = 3.4.dp.toPx())
        )
        drawCircle(
            Color.White.copy(alpha = 0.78f),
            base * 0.075f,
            Offset(c.x - base * 0.37f, c.y - base * 0.52f)
        )
    }
}

private fun assistantActionPreview(action: AssistantAction): String {
    val p = action.parameters
    val amount = p["amount"]?.toLongOrNull()?.let { java.text.NumberFormat.getNumberInstance(java.util.Locale("fa", "IR")).format(it) + " تومان" }
    return when (action.module) {
        AssistantModule.FINANCE -> (if (action.operation == "add_income") "ثبت درآمد" else "ثبت هزینه") + ": " + (amount ?: "مبلغ نامشخص") + "، دسته: " + p["category"].orEmpty() + "، شرح: " + p["note"].orEmpty()
        AssistantModule.SPORTS -> "ثبت تمرین «" + p["title"].orEmpty() + "»؛ " + p["minutes"].orEmpty() + " دقیقه، " + p["distance"].orEmpty() + " کیلومتر"
        AssistantModule.TRAVEL -> "ثبت هزینهٔ سفر: " + (amount ?: "مبلغ نامشخص") + "، عنوان: " + p["title"].orEmpty() + "، پرداخت‌کننده: " + p["payer"].orEmpty()
        AssistantModule.MEMORY -> "ذخیره در حافظه: «" + p["note"].orEmpty() + "»"
    }
}