package com.naeem.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.os.Bundle
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import com.naeem.ai.core.assistant.*
import java.io.File

@Composable
private fun GeminiStyleBackdrop(content: @Composable () -> Unit) {
    SupraGlassBackdrop { content() }
}

@Composable
private fun ModuleScreenBackdrop(
    module: String,
    content: @Composable () -> Unit
) {
    val palette = when (module.lowercase()) {
        "finance", "markets" -> listOf(Color(0xFF071A22), Color(0xFF102D3C), Color(0xFF111A2C))
        "health", "sports", "trail" -> listOf(Color(0xFF071D1B), Color(0xFF123A35), Color(0xFF101B2A))
        "travel" -> listOf(Color(0xFF0A1D35), Color(0xFF173C59), Color(0xFF161D32))
        "music", "podcast" -> listOf(Color(0xFF1B1230), Color(0xFF34234A), Color(0xFF10172B))
        "discover", "news" -> listOf(Color(0xFF101C35), Color(0xFF243657), Color(0xFF111827))
        "journal", "wrapped" -> listOf(Color(0xFF241B25), Color(0xFF3B2B3B), Color(0xFF171B2B))
        "games" -> listOf(Color(0xFF17142E), Color(0xFF2D2752), Color(0xFF10172A))
        "social" -> listOf(Color(0xFF1B172C), Color(0xFF3A2846), Color(0xFF111827))
        "hub" -> listOf(Color(0xFF0A1B30), Color(0xFF1C3450), Color(0xFF10182A))
        else -> listOf(Color(0xFF0A1728), Color(0xFF1A2C45), Color(0xFF101827))
    }
    val backdropRes = when (module.lowercase()) {
        "finance", "markets" -> R.drawable.bg_finance
        "travel" -> R.drawable.bg_travel
        "games" -> R.drawable.bg_games
        "journal", "wrapped" -> R.drawable.bg_wrapped
        "social", "music", "podcast" -> R.drawable.bg_art
        else -> R.drawable.bg_abstract
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(palette))) {
        Image(painter = painterResource(backdropRes), contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.34f }.blur(8.dp),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x33050B16)))
        Canvas(Modifier.fillMaxSize()) {
            val glow = when (module.lowercase()) {
                "finance", "markets" -> Color(0xFF35C7B5)
                "health", "sports", "trail" -> Color(0xFF55D6A5)
                "travel" -> Color(0xFF55B8E8)
                "music", "podcast" -> Color(0xFFB68BFF)
                "discover", "news" -> Color(0xFF82AFFF)
                "journal", "wrapped" -> Color(0xFFFFB47A)
                "games" -> Color(0xFF9D8CFF)
                "social" -> Color(0xFFE18CCB)
                else -> Color(0xFF82B8F4)
            }
            val point = Offset(size.width * 0.78f, size.height * 0.18f)
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.20f), glow.copy(alpha = 0.06f), Color.Transparent), point, size.maxDimension * 0.82f), size.maxDimension * 0.82f, point)
            val second = Offset(size.width * 0.12f, size.height * 0.78f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.055f), Color.Transparent), second, size.maxDimension * 0.58f), size.maxDimension * 0.58f, second)
        }
        content()
    }
}

private val NaeemPersianFont = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private fun naeemTypography(): Typography {
    val base = Typography()
    fun persian(style: androidx.compose.ui.text.TextStyle) =
        style.copy(fontFamily = NaeemPersianFont, textDirection = TextDirection.ContentOrRtl)
    return Typography(
        displayLarge = persian(base.displayLarge),
        displayMedium = persian(base.displayMedium),
        displaySmall = persian(base.displaySmall),
        headlineLarge = persian(base.headlineLarge),
        headlineMedium = persian(base.headlineMedium),
        headlineSmall = persian(base.headlineSmall),
        titleLarge = persian(base.titleLarge),
        titleMedium = persian(base.titleMedium),
        titleSmall = persian(base.titleSmall),
        bodyLarge = persian(base.bodyLarge),
        bodyMedium = persian(base.bodyMedium),
        bodySmall = persian(base.bodySmall),
        labelLarge = persian(base.labelLarge),
        labelMedium = persian(base.labelMedium),
        labelSmall = persian(base.labelSmall)
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
    private val introCompleted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val store = SecureConfigStore(this)
        val auth = SupabaseAuthClient(this)
        val authReady = mutableStateOf(false)
        val authSession = mutableStateOf<SupabaseSession?>(null)
        val profilePrefs = getSharedPreferences("naeem_user_profile", Context.MODE_PRIVATE)
        val appPrefs = getSharedPreferences("naeem_app_state", Context.MODE_PRIVATE)
        val profileCompleted = mutableStateOf(profilePrefs.getBoolean("profile_completed", false))
        introCompleted.value = false

        setContent {
            val authScope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                authSession.value = withContext(Dispatchers.IO) { auth.restoreSession() }
                authReady.value = true
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = SupraGlass.Ice,
                        onPrimary = Color(0xFF071426),
                        secondary = Color(0xFF8CC4FF),
                        onSecondary = Color(0xFF061426),
                        tertiary = Color(0xFFB8D8F8),
                        background = SupraGlass.Night,
                        onBackground = SupraGlass.Silver,
                        surface = Color(0xFF10243B),
                        onSurface = SupraGlass.Silver,
                        surfaceVariant = Color(0xFF172D47),
                        onSurfaceVariant = SupraGlass.Muted,
                        outline = Color(0x667DAFE4),
                        surfaceTint = SupraGlass.Ice
                    ),
                    typography = naeemTypography()
                ) {
                    SupraGlassBackdrop {
                    if (!introCompleted.value) {
                        AppIntegrationIntro(
                            onEnter = {
                                introCompleted.value = true
                            }
                        )
                    } else if (!authReady.value) {
                        Box(Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SupraGlass.Ice)
                        }
                    } else if (authSession.value == null) {
                        SupabaseAuthScreen(auth) { session -> authSession.value = session }
                    } else if (!profileCompleted.value) {
                        UserProfileSetup(onSave = { name, interests, about, style ->
                            profilePrefs.edit()
                                .putString("user_name", name)
                                .putString("user_interests", interests)
                                .putString("user_about", about)
                                .putString("response_style", style)
                                .putBoolean("profile_completed", true)
                                .apply()
                            profileCompleted.value = true
                        })
                    } else {
                        NaeemApp(
                            store,
                            onExternalActivityStarted = {},
                            onExternalActivityReturned = {},
                            userEmail = authSession.value?.email.orEmpty(),
                            chatAccessToken = authSession.value?.accessToken.orEmpty(),
                            onSignOut = {
                                authScope.launch {
                                    auth.signOut()
                                    authSession.value = null
                                }
                            }
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileSetup(onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("صمیمی و طبیعی") }
    val silver = Color(0xFFE0E5EC)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(painter = painterResource(R.drawable.bg_mountain), contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.56f }.blur(5.dp),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x77050B16)))
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThinkingOrb(diameter = 132.dp, active = true)
            Text("اول با هم آشنا بشیم", color = silver, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("چند مورد کوتاه بگو تا دستیار با اسم خودت صدات کنه و بهتر بشناسدت.", color = Color(0xFFB0BAC6), style = MaterialTheme.typography.bodyMedium)
            val profileFieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color(0xFFF1F4F8),
                focusedContainerColor = Color(0x1AFFFFFF), unfocusedContainerColor = Color(0x12FFFFFF),
                focusedBorderColor = Color(0xFFB7D2FF), unfocusedBorderColor = Color(0xFF71839D),
                focusedLabelColor = Color(0xFFB7D2FF), unfocusedLabelColor = Color(0xFFCAD4E2),
                focusedPlaceholderColor = Color(0xFF9EADC2), unfocusedPlaceholderColor = Color(0xFF9EADC2),
                cursorColor = Color(0xFFB7D2FF)
            )
            OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("دوست داری چی صدات کنم؟") }, placeholder = { Text("مثلاً نعیم") }, colors = profileFieldColors)
            OutlinedTextField(value = interests, onValueChange = { interests = it }, modifier = Modifier.fillMaxWidth(), label = { Text("به چه چیزهایی علاقه داری؟ (اختیاری)") }, placeholder = { Text("مثلاً طراحی، تکنولوژی، سفر…") }, minLines = 2, colors = profileFieldColors)
            OutlinedTextField(value = about, onValueChange = { about = it }, modifier = Modifier.fillMaxWidth(), label = { Text("چه چیزی خوبه درباره‌ات بدونم؟ (اختیاری)") }, placeholder = { Text("شغل، هدف‌ها یا موضوعات موردعلاقه…") }, minLines = 2, colors = profileFieldColors)
            Text("لحن گفت‌وگو", color = silver, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("صمیمی و طبیعی", "رسمی و دقیق", "کوتاه و مستقیم").forEach { option ->
                    FilterChip(selected = style == option, onClick = { style = option }, label = { Text(option, style = MaterialTheme.typography.labelSmall) })
                }
            }
            Button(onClick = { if (name.isNotBlank()) onSave(name.trim(), interests.trim(), about.trim(), style) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = silver, contentColor = Color(0xFF101318)), shape = RoundedCornerShape(17.dp)) {
                Text("ذخیره و ورود به برنامه", fontWeight = FontWeight.Bold)
            }
            Text("این اطلاعات فقط روی همین گوشی ذخیره می‌شوند و هر زمان می‌توانی تغییرشان بدهی.", color = Color(0xFF8995A5), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AppIntegrationIntro(onEnter: () -> Unit) {
    var page by remember { mutableIntStateOf(-1) }
    val silver = Color(0xFFE8EDF5)
    val muted = Color(0xFFB9C5D5)
    Box(Modifier.fillMaxSize().background(Color.Transparent)) {
        if (page == -1) {
            Image(painter = painterResource(R.drawable.bg_mountain), contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.58f }.blur(5.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x77050B16)))
            Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp).offset(y = (-8).dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = painterResource(R.drawable.ic_brain_mark), contentDescription = "لوگوی ایما",
                    modifier = Modifier.fillMaxWidth(0.86f).height(210.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.height(24.dp))
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    append("تو زندگی کن؛ ")
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFB9D8FF)))
                    append("ایما"); pop(); append(" حواسش هست.")
                }, color = Color.White, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
            }
            Surface(onClick = { page = 0 },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = 34.dp, vertical = 28.dp).fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp), color = Color(0x773A5676), contentColor = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x889DBDE5)),
                tonalElevation = 0.dp, shadowElevation = 0.dp) {
                Box(contentAlignment = Alignment.Center) { Text("شروع", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        } else {
            Image(painter = painterResource(R.drawable.bg_mountain), contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.58f }.blur(5.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x77050B16)))
            Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Image(painter = painterResource(R.drawable.ic_brain_mark), contentDescription = "لوگوی ایما", modifier = Modifier.size(74.dp))
                Spacer(Modifier.height(18.dp))
                if (page == 0) {
                    Text("ایما چیست؟", color = silver, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("هوشمندی فراتر از کلمات", color = Color(0xFFDCE8FA), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "ایما از مفهوم «ایما و اشاره» الهام گرفته است؛ یعنی دریافت منظور، حتی زمانی که همه‌چیز را مستقیم و دقیق بیان نمی‌کنی.\n\nلازم نیست همیشه دستور مشخصی داشته باشی. می‌توانی با ایما صحبت کنی، از اتفاقات روزمره، ایده‌ها و دغدغه‌هایت بگویی. ایما با فعال‌کردن قابلیت شنیدن، صحبت‌هایت را دریافت می‌کند و با اجازه تو، نکات مهم را به خاطر می‌سپارد.",
                        color = muted, style = MaterialTheme.typography.bodyLarge, lineHeight = 29.sp, textAlign = TextAlign.End)
                } else {
                    Text("ایما چه کارهایی انجام می‌دهد؟", color = silver, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "ایما فقط یک پنجره‌ی گفتگو نیست؛ موضوعات را می‌شناسد و آن‌ها را به فضای مناسب می‌برد. می‌توانی برای مدیریت زندگی روزمره، برنامه‌ریزی کارها و هدف‌ها، یادآوری‌ها و همراهی پروژه‌ها از آن کمک بگیری.\n\nدر فضاهای تخصصی، بخش‌هایی برای سفر و طبیعت‌گردی، ورزش، سلامت و تغذیه، امور مالی، روزنگار و حافظه در نظر گرفته شده است.\n\nبرای جست‌وجو و پژوهش، اتاق فکر، اخبار و کشف محتوا؛ و برای خلاقیت، ساخت تصویر و ویدیو، تدوین، موسیقی و پادکست هم ابزارهایی در اختیار داری. اتصال به کامپیوتر و بخش‌های دیگر نیز از فضاهای ایما قابل دسترسی‌اند. بعضی قابلیت‌ها به اتصال سرویس یا تکمیل توسعه نیاز دارند.",
                        color = muted, style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp, textAlign = TextAlign.End)
                    Spacer(Modifier.height(20.dp))
                    Text("تو زندگی کن؛ ایما حواسش هست.", color = silver, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    repeat(2) { index ->
                        Box(Modifier.padding(horizontal = 4.dp).size(if (page == index) 9.dp else 7.dp)
                            .background(if (page == index) Color(0xFFDCE8FA) else Color(0x667D97BA), CircleShape))
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (page == 0) {
                    Button(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCE8FA), contentColor = Color(0xFF101A2A)),
                        shape = RoundedCornerShape(17.dp)) { Text("ادامه", fontWeight = FontWeight.Bold) }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { page = 0 }, modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(17.dp)) { Text("قبلی", color = silver) }
                        Button(onClick = onEnter, modifier = Modifier.weight(1f).height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCE8FA), contentColor = Color(0xFF101A2A)),
                            shape = RoundedCornerShape(17.dp)) { Text("ورود به ایما", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
private val Ink = SupraGlass.Night
private val Panel = SupraGlass.Glass
private val Gold = SupraGlass.Ice
private val Muted = SupraGlass.Muted


@Composable
private fun HomeShortcut(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.height(112.dp).clickable { onClick() }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SupraGlass.Glass), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.19f), SupraGlass.Glass))).padding(13.dp)) {
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
fun NaeemApp(
    store: SecureConfigStore,
    onExternalActivityStarted: () -> Unit,
    onExternalActivityReturned: () -> Unit,
    onSignOut: () -> Unit = {},
    userEmail: String = "",
    chatAccessToken: String = ""
) {
    val context = LocalContext.current
    val profilePrefs = remember { context.getSharedPreferences("naeem_user_profile", Context.MODE_PRIVATE) }
    val userName = remember { profilePrefs.getString("user_name", "").orEmpty() }
    val userInterests = remember { profilePrefs.getString("user_interests", "").orEmpty() }
    val userAbout = remember { profilePrefs.getString("user_about", "").orEmpty() }
    val responseStyle = remember { profilePrefs.getString("response_style", "صمیمی و طبیعی").orEmpty() }
    val onboardingPrefs = remember { context.getSharedPreferences("naeem_onboarding", Context.MODE_PRIVATE) }
    var tutorialStep by remember { mutableStateOf(if (onboardingPrefs.getBoolean("guided_tour_completed", false)) -1 else 0) }
    var micTargetBounds by remember { mutableStateOf(Rect.Zero) }
    var inputTargetBounds by remember { mutableStateOf(Rect.Zero) }
    var sendTargetBounds by remember { mutableStateOf(Rect.Zero) }
    var menuTargetBounds by remember { mutableStateOf(Rect.Zero) }
    var showSettings by remember { mutableStateOf(false) }
    var showPcControl by remember { mutableStateOf(false) }
    var showProfileDetails by remember { mutableStateOf(false) }
    var showCreatorInfo by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var welcomeAudioEnabled by remember {
        mutableStateOf(context.getSharedPreferences("naeem_app_state", Context.MODE_PRIVATE)
            .getBoolean("welcome_audio_enabled", true))
    }
    var baseUrl by remember { mutableStateOf(store.baseUrl()) }
    var model by remember { mutableStateOf(store.model()) }
    var apiKey by remember { mutableStateOf(store.apiKeyFor(store.baseUrl()).ifBlank { store.apiKey() }) }
    var pendingApiConfig by remember { mutableStateOf<ParsedApiConfig?>(null) }
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
    var selectedModelType by remember { mutableStateOf("chat") }
    var testingProvider by remember { mutableStateOf(false) }
    fun switchApiProvider(url: String) {
        store.rememberApiKey(baseUrl, apiKey)
        baseUrl = url
        apiKey = store.apiKeyFor(url)
        providerTestStatus = if (apiKey.isBlank()) "برای این درگاه هنوز کلید ذخیره نشده؛ کلید مربوط به همان سرویس را وارد کن." else ""
        availableModels = emptyList()
    }
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
                    HealthAssistantTool(context.applicationContext),
                    TravelAssistantTool(context.applicationContext),
                    MemoryAssistantTool(dataStore),
                    JournalAssistantTool(context.applicationContext)
                )
            )
        )
    }
    var pendingAssistantAction by remember { mutableStateOf<AssistantAction?>(null) }
    var pendingJournalDetail by remember { mutableStateOf<String?>(null) }
    var showFinance by remember { mutableStateOf(false) }
    var showHub by remember { mutableStateOf(false) }
    var showHealth by remember { mutableStateOf(false) }
    var showJournal by remember { mutableStateOf(false) }
    var showGames by remember { mutableStateOf(false) }
    var showDiscover by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(false) }
    var showPodcast by remember { mutableStateOf(false) }
    var showVideoCreator by remember { mutableStateOf(false) }
    var showProfessionalVideo by remember { mutableStateOf(false) }
    var showTravel by remember { mutableStateOf(false) }
    var showSports by remember { mutableStateOf(false) }
    var showTrail by remember { mutableStateOf(false) }
    var showSocialAnalytics by remember { mutableStateOf(false) }
    var showMarkets by remember { mutableStateOf(false) }
    var showWrapped by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var showAlbum by remember { mutableStateOf(false) }
    var showNews by remember { mutableStateOf(false) }
    var showMedication by remember { mutableStateOf(false) }
    var showPlanner by remember { mutableStateOf(false) }
    var showThinkRoom by remember { mutableStateOf(false) }
    var showFutureGateway by remember { mutableStateOf(false) }
    var memoryDraft by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var showVideoDevelopment by remember { mutableStateOf(false) }
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
    suspend fun sendChat(text: String, image: String? = null): String {
        val gateway = BuildConfig.CHAT_GATEWAY_URL.trim()
        return if (gateway.isNotBlank()) {
            client.sendViaGateway(gateway, chatAccessToken, model, text, image)
        } else {
            client.send(baseUrl, model, apiKey, text, image)
        }
    }
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
                    val audioBytes = audioClient.synthesize(baseUrl, apiKey, textChunk, store.speechModel())
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
        val journal = DailyJournalStore(context)
        fun mirrorSuccessfulWrite(module: AssistantModule, operation: String) {
            if (question.contains("روزنگار") || question.contains("گزارش روزانه")) return
            val captured = journal.captureNarrative(question)
            if (captured.isEmpty()) {
                val category = when (module) {
                    AssistantModule.SPORTS -> "ورزش"
                    AssistantModule.HEALTH -> "تغذیه و سلامت"
                    AssistantModule.TRAVEL -> "سفر و طبیعت"
                    AssistantModule.FINANCE -> "مالی و خرید"
                    else -> "شخصی"
                }
                journal.add(category, operation, question)
            }
        }
        return when (val resolution = PersianAssistantIntentResolver.resolve(question, TravelStore(context).load())) {
            AssistantIntentResolution.NoMatch -> {
                val captured = if (question.contains("روزنگار") || question.contains("گزارش روزانه")) emptyList() else journal.captureNarrative(question)
                if (captured.isEmpty()) null else "روایتت در روزنگار ثبت شد (${captured.size} مورد)."
            }
            is AssistantIntentResolution.Clarify -> resolution.question
            is AssistantIntentResolution.Ready -> when (val routed = assistantRouter.route(resolution.action)) {
                is ActionRouteResult.Completed -> {
                    ledgerEntries = ledgerStore.entries()
                    memories = dataStore.memories()
                    when (resolution.action.module) {
                        AssistantModule.FINANCE -> showFinance = true
                        AssistantModule.SPORTS -> showSports = true
                        AssistantModule.HEALTH -> showHealth = true
                        AssistantModule.TRAVEL -> showTravel = true
                        else -> Unit
                    }
                    if (resolution.action.access == ActionAccess.WRITE && routed.result.success)
                        mirrorSuccessfulWrite(resolution.action.module, resolution.action.operation)
                    routed.result.message
                }
                is ActionRouteResult.ConfirmationRequired -> {
                    pendingAssistantAction = routed.action
                    pendingJournalDetail = question
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
                    when (action.module) {
                        AssistantModule.FINANCE -> showFinance = true
                        AssistantModule.SPORTS -> showSports = true
                        AssistantModule.HEALTH -> showHealth = true
                        AssistantModule.TRAVEL -> showTravel = true
                        else -> Unit
                    }
                    pendingJournalDetail?.let { detail ->
                        val journal = DailyJournalStore(context)
                        val captured = journal.captureNarrative(detail)
                        if (captured.isEmpty()) {
                            val category = when (action.module) {
                                AssistantModule.SPORTS -> "ورزش"
                                AssistantModule.HEALTH -> "تغذیه و سلامت"
                                AssistantModule.TRAVEL -> "سفر و طبیعت"
                                AssistantModule.FINANCE -> "مالی و خرید"
                                else -> "شخصی"
                            }
                            journal.add(category, action.operation, detail)
                        }
                    }
                    pendingJournalDetail = null
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
                    val answer = sendChat(question)
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

    LaunchedEffect(Unit) {
        if (welcomeAudioEnabled && apiKey.isNotBlank()) {
            delay(850)
            if (!busy && !speechPlaying) playAnswer("به ایما خوش آمدید. خوشحالم که دوباره اینجایی. هر وقت آماده‌ای، از من چیزی بپرس یا با صدای خودت صحبت کن.")
        }
    }

    fun sendMessage(speakAnswer: Boolean = false) {
        val question = prompt.trim()
        val imageToSend = selectedImageBase64
        val imagePreview = selectedImage
        if ((question.isBlank() && imageToSend.isNullOrBlank()) || busy) return
        // Intercept labelled credentials locally before they can enter chat history or leave the device.
        val parsedApiConfig = if (imageToSend.isNullOrBlank()) ApiConfigTextParser.parse(question) else null
        if (parsedApiConfig != null) {
            pendingApiConfig = parsedApiConfig
            prompt = ""
            error = ""
            return
        }
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
                    val profileContext = buildString {
                        if (userName.isNotBlank()) append("\n\nنامی که کاربر دوست دارد با آن خطاب شود: $userName. در شروع گفت‌وگو و در صورت طبیعی بودن، او را با همین نام خطاب کن؛ از تکرار بیش از حد اسم خودداری کن.")
                        if (userInterests.isNotBlank()) append("\nعلایق کاربر: $userInterests.")
                        if (userAbout.isNotBlank()) append("\nدرباره کاربر: $userAbout.")
                        append("\nلحن ترجیحی پاسخ: $responseStyle.")
                    }
                    val userContext = profileContext + memoryContext
                    val answer = if (searchMode) {
                        val findings = deepSearchClient.search(finalQuestion, 8)
                        val sourceText = findings.mapIndexed { index, item -> "[$index] ${item.title}\n${item.snippet}\nURL: ${item.url}" }.joinToString("\n\n")
                        val researchPrompt = "برای پرسش زیر یک گزارش پژوهشی دقیق و فارسی بنویس. از منابع زیر استفاده کن، ادعاها را به شماره منبع ارجاع بده، اختلاف اطلاعات را مشخص کن و در پایان فهرست منابع را با URL کامل بیاور. اگر منابع کافی نیستند صادقانه بگو.\n\nپرسش: $finalQuestion\n\nمنابع وب:\n$sourceText$userContext"
                        val report = sendChat(researchPrompt)
                        report + "\n\nمنابع بررسی‌شده:\n" + findings.mapIndexed { index, item -> "[${index + 1}] ${item.title} — ${item.url}" }.joinToString("\n")
                    } else {
                        val memoryAwarePrompt = "زمینه کاربر:\n$userContext\n\nدرخواست کاربر:\n$finalQuestion"
                        if (finalQuestion.contains("به خاطر بسپار") || finalQuestion.contains("یادت باشه")) {
                            val note = finalQuestion.replace("به خاطر بسپار", "").replace("یادت باشه", "").trim()
                            if (note.isNotBlank()) { dataStore.addMemory(note); memories = dataStore.memories() }
                        }
                        sendChat(memoryAwarePrompt, imageToSend)
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

    val chatFlow = rememberInfiniteTransition(label = "chat-flow")
    val chatFlowPosition by chatFlow.animateFloat(0f, 1f,
        infiniteRepeatable(tween(7200, easing = LinearEasing), RepeatMode.Reverse),
        label = "chat-flow-position")

    MaterialTheme(
        typography = naeemTypography(),
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Ink, background = Ink, onBackground = Color(0xFFE7ECF2),
            surface = Panel, onSurface = Color(0xFFE7ECF2), surfaceVariant = SupraGlass.Glass,
            onSurfaceVariant = Color(0xFFBFC6D0), secondary = Color(0xFFBFC6D0)
        )
    ) {
        if (showVideoDevelopment) {
            Dialog(
                onDismissRequest = { showVideoDevelopment = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    shape = RoundedCornerShape(0.dp),
                    color = Color(0xFF050B16)
                ) {
                    ModuleScreenBackdrop("music") {
                        VideoCreatorDashboard(onClose = { showVideoDevelopment = false }, initialPrompt = prompt)
                    }
                }
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalDrawerSheet(
                    modifier = Modifier.width(304.dp),
                    drawerContainerColor = Color(0xFF0B1728),
                    drawerContentColor = Color(0xFFEAF1FC)
                ) {
                    Column(Modifier.fillMaxHeight().padding(horizontal = 14.dp, vertical = 18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(painter = painterResource(R.drawable.ic_brain_mark), contentDescription = "لوگوی ایما", modifier = Modifier.size(52.dp))
                            Column {
                                Text("ایما", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                Text("فضای شخصی هوشمند", color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider(color = Color(0x334F6B8C), modifier = Modifier.padding(vertical = 10.dp))
                        NavigationDrawerItem(label = { Text("فضاهای من و ابزارها") }, selected = false,
                            icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close(); showHub = true } })
                        NavigationDrawerItem(label = { Text("اتصال به کامپیوتر") }, selected = false,
                            icon = { Icon(Icons.Default.Computer, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close(); showPcControl = true } })
                        NavigationDrawerItem(label = { Text("مشخصات حساب من") }, selected = false,
                            icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close(); showProfileDetails = true } })
                        NavigationDrawerItem(label = { Text("درباره سازنده") }, selected = false,
                            icon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close(); showCreatorInfo = true } })
                        HorizontalDivider(color = Color(0x334F6B8C), modifier = Modifier.padding(vertical = 10.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("خوش‌آمدگویی صوتی", color = Color.White, fontWeight = FontWeight.Medium)
                                Text("پخش هنگام ورود به ایما", color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = welcomeAudioEnabled, onCheckedChange = {
                                welcomeAudioEnabled = it
                                context.getSharedPreferences("naeem_app_state", Context.MODE_PRIVATE)
                                    .edit().putBoolean("welcome_audio_enabled", it).apply()
                            })
                        }
                        Spacer(Modifier.weight(1f))
                        Text("EMA ASI PLATFORM", color = Color(0xFF7186A3),
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp))
                    }
                }
                }
            }
        ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brain_mark),
                            contentDescription = "لوگوی ایما",
                            modifier = Modifier.size(58.dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.onGloballyPositioned { menuTargetBounds = it.boundsInWindow() }) {
                            Icon(Icons.Default.Menu, contentDescription = "باز کردن نوار ابزار", tint = Gold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showHub = true }) {
                            Icon(Icons.Default.Apps, contentDescription = "فضاهای من و ابزارها", tint = Gold)
                        }
                        IconButton(onClick = {
                            conversationStore.save(activeConversationId, messages)
                            activeConversationId = conversationStore.create()
                            conversationReload++
                            pendingAssistantAction = null
                            pendingJournalDetail = null
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                Surface(
                    color = Color(0xCC10243B),
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(25.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (selectedImage != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Image(bitmap = selectedImage!!, contentDescription = "تصویر انتخاب‌شده", modifier = Modifier.size(64.dp), contentScale = ContentScale.Crop)
                                Text("تصویر آماده ارسال است", color = Muted, style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = { selectedImage = null; selectedImageBase64 = null }) { Text("حذف", color = Gold) }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { imagePicker.launch("image/*") },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                            ) {
                                Text("＋ عکس", color = Gold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                            TextButton(
                                onClick = { imageMode = !imageMode; if (imageMode) { selectedImage = null; selectedImageBase64 = null } },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                            ) {
                                Text(if (imageMode) "✦ تصویر روشن" else "✦ ساخت تصویر", color = if (imageMode) Color(0xFFD4D9E0) else Gold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                            TextButton(
                                onClick = { searchMode = !searchMode },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                            ) {
                                Text(if (searchMode) "جست‌وجو روشن" else "⌕ جست‌وجوی عمیق", color = if (searchMode) Color(0xFFD4D9E0) else Gold, fontSize = 10.sp, maxLines = 1, softWrap = false)
                            }
                            TextButton(
                                onClick = { showVideoDevelopment = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                            ) {
                                Text("▶ ویدیو", color = Gold, fontSize = 11.sp, maxLines = 1, softWrap = false)
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
                                modifier = Modifier.size(48.dp).background(if (conversationMode || listening) Gold else Panel, CircleShape).onGloballyPositioned { micTargetBounds = it.boundsInWindow() }
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = if (conversationMode) "پایان مکالمه صوتی" else "شروع مکالمه صوتی", tint = if (conversationMode || listening) Ink else Gold)
                            }
                            OutlinedTextField(
                                value = prompt, onValueChange = { prompt = it },
                                modifier = Modifier.weight(1f).onGloballyPositioned { inputTargetBounds = it.boundsInWindow() },
                                placeholder = { Text(if (imageMode) "توضیح تصویری که می‌خواهی بسازم…" else if (conversationMode) "حالت مکالمه صوتی فعاله…" else "پیامت رو بنویس…", color = Muted) },
                                maxLines = 4, shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF3A3D45),
                                    focusedContainerColor = Color(0x16FFFFFF), unfocusedContainerColor = Color(0x10FFFFFF)
                                )
                            )
                            IconButton(
                                onClick = { sendMessage(false) },
                                enabled = !busy && (prompt.isNotBlank() || !selectedImageBase64.isNullOrBlank()),
                                modifier = Modifier.size(48.dp).background(if (!busy && (prompt.isNotBlank() || !selectedImageBase64.isNullOrBlank())) Gold else Panel, CircleShape).onGloballyPositioned { sendTargetBounds = it.boundsInWindow() }
                            ) {
                                Icon(Icons.Default.Send, contentDescription = if (imageMode) "ساخت تصویر" else "ارسال", tint = if (!busy && (prompt.isNotBlank() || !selectedImageBase64.isNullOrBlank())) Ink else Muted)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize()
                    .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF070B14), Color(0xFF0B1424), Color(0xFF14263D), Color(0xFF080D18))
                    )
                )
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color(0x334B8DCE), Color(0x183B68A8), Color.Transparent),
                                center = Offset(size.width * 0.5f, size.height * (0.92f - chatFlowPosition * 0.50f)),
                                radius = size.height * 0.92f
                            ),
                            radius = size.height * 0.92f,
                            center = Offset(size.width * 0.5f, size.height * (0.92f - chatFlowPosition * 0.50f))
                        )
                    }
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                if (messages.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().weight(1f).padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "سلام " + userName.ifBlank { "دوست من" },
                            color = Color(0xFFEAF1FC),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "امروز دوست داری درباره چی صحبت کنیم؟",
                            color = Color(0xFFAFBED2),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(34.dp))
                        ThinkingOrb(diameter = 204.dp, active = true, onClick = { playThinkingOrbSound(context) })
                        Spacer(Modifier.height(20.dp))
                        Image(painter = painterResource(R.drawable.ic_brain_mark),
                            contentDescription = "لوگوی ایما", modifier = Modifier.size(92.dp), contentScale = ContentScale.Fit)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "لمس گوی یا نوشتن پیام برای شروع",
                            color = Color(0xFF879AB5),
                            style = MaterialTheme.typography.labelMedium
                        )
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
                        colors = CardDefaults.cardColors(containerColor = Color(0x2B9CCBFF))
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color(0x22D7E9FF), Color(0x0C8CC4FF))))
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
                            Text("گوی هوشمند ایما", color = Muted, style = MaterialTheme.typography.labelSmall)
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
                        colors = CardDefaults.cardColors(containerColor = SupraGlass.Glass),
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
                                modifier = Modifier.fillMaxWidth(0.90f),
                                shape = if (msg.fromUser) RoundedCornerShape(22.dp, 22.dp, 7.dp, 22.dp) else RoundedCornerShape(7.dp, 22.dp, 22.dp, 22.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (msg.fromUser) Gold.copy(alpha = 0.22f) else Color(0xFF49443A).copy(alpha = 0.48f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = if (msg.fromUser) Color(0x443D86C8) else Color(0x1AFFFFFF))
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(if (msg.fromUser) "شما" else "ایما", color = Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
        }
        }
        }
        if (showProfileDetails) {
            AlertDialog(onDismissRequest = { showProfileDetails = false }, containerColor = Panel,
                title = { Text("مشخصات حساب من", color = Gold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("نام نمایشی: " + userName.ifBlank { "ثبت نشده" }, color = Color.White)
                        Text("ایمیل ورود: " + userEmail.ifBlank { "در دسترس نیست" }, color = Color.White)
                        Text("علایق: " + userInterests.ifBlank { "ثبت نشده" }, color = Muted)
                        Text("درباره من: " + userAbout.ifBlank { "ثبت نشده" }, color = Muted)
                        Text("لحن پاسخ: " + responseStyle, color = Muted)
                    }
                },
                confirmButton = { TextButton(onClick = { showProfileDetails = false }) { Text("بستن", color = Gold) } })
        }
        if (showCreatorInfo) {
            AlertDialog(onDismissRequest = { showCreatorInfo = false }, containerColor = Panel,
                title = { Text("درباره سازنده", color = Gold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painter = painterResource(R.drawable.ic_brain_mark), contentDescription = "لوگوی ایما", modifier = Modifier.size(72.dp))
                        Text("Naeem AI Studio", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("ایما با طراحی و توسعه نعیم دهقانی ساخته شده؛ یک دستیار هوشمند برای گفت‌وگو، خلاقیت، پژوهش و مدیریت فضاهای شخصی.", color = Muted, textAlign = TextAlign.Center)
                        Text("EMA ASI PLATFORM", color = Color(0xFF9EBBE5), style = MaterialTheme.typography.labelMedium)
                    }
                },
                confirmButton = { TextButton(onClick = { showCreatorInfo = false }) { Text("بستن", color = Gold) } })
        }

        if (tutorialStep >= 0) {
            val target = when (guidedTourItems[tutorialStep].target) {
                "میکروفون" -> micTargetBounds
                "کادر نوشتن پیام" -> inputTargetBounds
                "ارسال پیام" -> sendTargetBounds
                "منوی فضاها" -> menuTargetBounds
                else -> Rect.Zero
            }
            GuidedTourOverlay(step = tutorialStep, targetBounds = target,
                onPrevious = { if (tutorialStep > 0) tutorialStep-- },
                onNext = {
                    if (tutorialStep >= guidedTourItems.lastIndex) {
                        onboardingPrefs.edit().putBoolean("guided_tour_completed", true).apply()
                        tutorialStep = -1
                    } else tutorialStep++
                }
            )
        }

        if (showPcControl) {
            Dialog(onDismissRequest = { showPcControl = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("hub") {
                        PcControlScreen(
                            onClose = { showPcControl = false },
                            baseUrl = baseUrl,
                            model = model,
                            apiKey = apiKey
                        )
                    }
                }
            }
        }

        if (showHub) {
            Dialog(onDismissRequest = { showHub = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize()) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("hub") { FeatureHub(
                        onClose = { showHub = false },
                        onFinance = { showHub = false; showFinance = true; ledgerEntries = ledgerStore.entries() },
                        onHealth = { showHub = false; showHealth = true },
                        onGames = { showHub = false; showGames = true },
                        onDiscover = { showHub = false; showDiscover = true },
                        onMusic = { showHub = false; showMusic = true },
                        onPodcast = { showHub = false; showPodcast = true },
                        onVideo = { showHub = false; showVideoCreator = true },
                        onMemory = { showHub = false; showMemory = true; memories = dataStore.memories() },
                        onAlbum = { showHub = false; showAlbum = true; albumImages = dataStore.images() },
                        onSettings = { showHub = false; showSettings = true },
                        onTravel = { showHub = false; showTravel = true },
                        onSport = { showHub = false; showSports = true },
                        onMarkets = { showHub = false; showMarkets = true },
                        onWrapped = { showHub = false; showWrapped = true },
                        onJournal = { showHub = false; showJournal = true },
                        onTrail = { showHub = false; showTrail = true },
                        onSocial = { showHub = false; showSocialAnalytics = true },
                        onNews = { showHub = false; showNews = true },
                        onMedication = { showHub = false; showMedication = true },
                        onPlanner = { showHub = false; showPlanner = true },
                        onThinkRoom = { showHub = false; showThinkRoom = true },
                        onFuture = { showHub = false; showFutureGateway = true },
                         onProfessionalVideo = { showHub = false; showProfessionalVideo = true },
                         onPcControl = { showHub = false; showPcControl = true }
                    ) }
                }
                }
            }
        }
        if (showJournal) {
            Dialog(onDismissRequest = { showJournal = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("journal") { DailyJournalDashboard(onClose = { showJournal = false }) }
                }
            }
        }
        if (showNews) {
            Dialog(onDismissRequest = { showNews = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("discover") { NewsDashboard(onClose = { showNews = false }) }
                }
            }
        }
        if (showMedication) {
            Dialog(onDismissRequest = { showMedication = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("health") { MedicationReminderDashboard(onClose = { showMedication = false }) }
                }
            }
        }
        if (showPlanner) {
            Dialog(onDismissRequest = { showPlanner = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("wrapped") { WeeklyPlannerDashboard(onClose = { showPlanner = false }) }
                }
            }
        }
        if (showThinkRoom) {
            Dialog(onDismissRequest = { showThinkRoom = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("discover") {
                        ThinkRoomScreen(
                            onClose = { showThinkRoom = false },
                            baseUrl = baseUrl,
                            model = model,
                            apiKey = apiKey
                        )
                    }
                }
            }
        }
        if (showFutureGateway) {
            Dialog(onDismissRequest = { showFutureGateway = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("wrapped") {
                        FutureGatewayScreen(
                            onClose = { showFutureGateway = false },
                            baseUrl = baseUrl,
                            model = model,
                            apiKey = apiKey
                        )
                    }
                }
            }
        }
        if (showTravel) {
            Dialog(onDismissRequest = { showTravel = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("travel") { TravelDashboard(onClose = { showTravel = false }) }
                }
            }
        }
        if (showTrail) {
            Dialog(onDismissRequest = { showTrail = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("trail") { TrailNavigationDashboard(onClose = { showTrail = false }) }
                }
            }
        }
        if (showWrapped) {
            Dialog(onDismissRequest = { showWrapped = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("wrapped") { MonthlyWrappedDashboard(onClose = { showWrapped = false }) }
                }
            }
        }
        if (showMarkets) {
            Dialog(onDismissRequest = { showMarkets = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("markets") { MarketsDashboard(onClose = { showMarkets = false }) }
                }
            }
        }
        if (showSports) {
            Dialog(onDismissRequest = { showSports = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("sports") { SportsDashboard(onClose = { showSports = false }) }
                }
            }
        }
        if (showHealth) {
            Dialog(onDismissRequest = { showHealth = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("health") { HealthDashboard(onClose = { showHealth = false }) }
                }
            }
        }
        if (showGames) {
            Dialog(onDismissRequest = { showGames = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("games") { GamesHub(onClose = { showGames = false }) }
                }
            }
        }
        if (showDiscover) {
            Dialog(onDismissRequest = { showDiscover = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("discover") { DiscoverDashboard(onClose = { showDiscover = false }, onAskAi = { question -> showDiscover = false; prompt = question }) }
                }
            }
        }
        if (showPodcast) {
            Dialog(onDismissRequest = { showPodcast = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("music") { PodcastDashboard(onClose = { showPodcast = false }) }
                }
            }
        }
        if (showProfessionalVideo) {
            Dialog(onDismissRequest = { showProfessionalVideo = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("music") {
                        ProfessionalVideoDashboard(onClose = { showProfessionalVideo = false }, baseUrl = baseUrl, apiKey = apiKey)
                    }
                }
            }
        }
        if (showVideoCreator) {
            Dialog(onDismissRequest = { showVideoCreator = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("music") {
                        SupraStudioDashboard(
                            onClose = { showVideoCreator = false },
                            onExternalActivityStarted = onExternalActivityStarted,
                            onExternalActivityReturned = onExternalActivityReturned
                        )
                    }
                }
            }
        }
        if (showMusic) {
            Dialog(onDismissRequest = { showMusic = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("music") { MusicDashboard(onClose = { showMusic = false }, onAskAi = { question -> showMusic = false; prompt = question }) }
                }
            }
        }
        if (showSocialAnalytics) {
            Dialog(onDismissRequest = { showSocialAnalytics = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("social") {
                        SocialAnalyticsDashboard(
                            onClose = { showSocialAnalytics = false },
                            onAskAi = { question -> showSocialAnalytics = false; prompt = question }
                        )
                    }
                }
            }
        }

        if (showFinance) {
            Dialog(onDismissRequest = { showFinance = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), shape = RoundedCornerShape(0.dp), color = Color(0xFF050B16)) {
                    ModuleScreenBackdrop("finance") {
                        FinanceDashboard(
                            entries = ledgerEntries,
                            onAdd = { amount, category, note, income -> ledgerStore.add(amount, category, note, income); ledgerEntries = ledgerStore.entries() },
                            onDelete = { id -> ledgerStore.delete(id); ledgerEntries = ledgerStore.entries() },
                            onClose = { showFinance = false }
                        )
                    }
                }
            }
        }

        if (showMemory) {
            AlertDialog(
                onDismissRequest = { showMemory = false },
                containerColor = Panel,
                title = { Text("حافظه ایما", color = Gold) },
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
                                Card(colors = CardDefaults.cardColors(containerColor = SupraGlass.Glass)) {
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
                                    Card(colors = CardDefaults.cardColors(containerColor = SupraGlass.Glass)) {
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

        pendingApiConfig?.let { config ->
            AlertDialog(
                onDismissRequest = { pendingApiConfig = null },
                containerColor = Panel,
                title = { Text("تنظیم خودکار API", color = Gold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اطلاعات اتصال شناسایی شد. قبل از ذخیره مرور کن:", color = Color.White)
                        Text("آدرس سرور: ${config.baseUrl}", color = Color(0xFFDCE3ED), style = MaterialTheme.typography.bodySmall)
                        Text("کلید API: شناسایی شد •••••••• (نمایش داده نمی‌شود)", color = Color(0xFFB9E5C9), style = MaterialTheme.typography.bodySmall)
                        config.chatModel?.let { Text("مدل چت: $it", color = Color(0xFFDCE3ED), style = MaterialTheme.typography.bodySmall) }
                        config.imageModel?.let { Text("مدل تصویر: $it", color = Color(0xFFDCE3ED), style = MaterialTheme.typography.bodySmall) }
                        config.videoModel?.let { Text("مدل ویدیو: $it", color = Color(0xFFDCE3ED), style = MaterialTheme.typography.bodySmall) }
                        config.speechModel?.let { Text("مدل گفتار: $it", color = Color(0xFFDCE3ED), style = MaterialTheme.typography.bodySmall) }
                        Text("کلید به‌صورت رمزگذاری‌شده روی گوشی ذخیره می‌شود و متن حاوی آن وارد تاریخچه چت یا درخواست مدل نمی‌شود.", color = Color(0xFFB9E5C9), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val selected = pendingApiConfig ?: return@TextButton
                        store.save(selected.baseUrl, selected.chatModel ?: model, selected.apiKey)
                        baseUrl = selected.baseUrl
                        apiKey = selected.apiKey
                        selected.chatModel?.let { model = it }
                        selected.imageModel?.let { imageModel = it; store.saveImageModel(it) }
                        selected.videoModel?.let { store.saveModelFor("video", it) }
                        selected.speechModel?.let { store.saveModelFor("audio_tts", it) }
                        selected.chatModel?.let { store.saveModelFor("chat", it) }
                        providerTestStatus = "تنظیمات API با موفقیت روی گوشی ذخیره شد."
                        pendingApiConfig = null
                    }) { Text("ذخیره امن تنظیمات", color = Gold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingApiConfig = null }) { Text("لغو", color = Muted) }
                }
            )
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = Panel,
                title = { Text("تنظیمات اتصال", color = Gold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("درگاه اتصال")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            OutlinedButton(onClick = { switchApiProvider("https://netarz.ir/api/ai/v1"); model = "gpt-4o-mini" }) { Text("نت‌ارز", color = Gold) }
                            OutlinedButton(onClick = { switchApiProvider("https://api.openai.com/v1"); model = "gpt-4o-mini" }) { Text("OpenAI مستقیم", color = Gold) }
                            OutlinedButton(onClick = { switchApiProvider("https://api.gapgpt.ir/v1"); model = "gpt-4o-mini" }) { Text("GapGPT", color = Gold) }
                        }
                        Text("نشانی سرویس (Base URL)")
                        OutlinedTextField(baseUrl, { baseUrl = it }, singleLine = true)
                        Text("نام مدل گفتگو")
                        OutlinedTextField(model, { model = it }, singleLine = true)
                        Text("شناسه مدل ساخت تصویر")
                        OutlinedTextField(imageModel, { imageModel = it }, singleLine = true)
                        Text("نوع مدل برای دریافت فهرست")
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("chat" to "چت", "image" to "تصویر", "audio_tts" to "متن به صدا", "audio_stt" to "صدا به متن", "video" to "ویدیو", "music" to "موسیقی", "embedding" to "حافظه").forEach { (type, label) ->
                                FilterChip(selected = selectedModelType == type, onClick = { selectedModelType = type; availableModels = emptyList(); providerTestStatus = "" }, label = { Text(label) })
                            }
                        }
                        Button(
                            onClick = {
                                testingProvider = true
                                providerTestStatus = ""
                                scope.launch {
                                    try {
                                        availableModels = client.fetchModels(baseUrl, apiKey, selectedModelType)
                                        providerTestStatus = "اتصال موفق؛ " + availableModels.size + " شناسه مدل دریافت شد."
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
                            Text("مدل‌های دسته انتخاب‌شده؛ روی شناسه بزن تا برای همان قابلیت ذخیره شود:", color = Muted, style = MaterialTheme.typography.labelSmall)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                availableModels.take(30).forEach { modelId ->
                                    TextButton(onClick = {
                                        store.saveModelFor(selectedModelType, modelId)
                                        when (selectedModelType) { "chat" -> model = modelId; "image" -> imageModel = modelId }
                                        providerTestStatus = "مدل $modelId برای دسته $selectedModelType ذخیره شد."
                                    }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                        Text(modelId, color = if ((selectedModelType == "chat" && model == modelId) || (selectedModelType == "image" && imageModel == modelId) || store.modelFor(selectedModelType) == modelId) Gold else Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Text("کلید API")
                        OutlinedTextField(apiKey, { apiKey = it }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Text("هشدار امنیتی: رمزگذاری گوشی مفید است، اما طبق توصیه نت‌ارز کلید API نباید در نسخه عمومی اپ قرار بگیرد. برای انتشار امن باید درخواست‌ها از سرور واسط اختصاصی عبور کنند.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD28A))
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
                                onClick = { backupExportLauncher.launch("EMA-Backup.json") },
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
                        store.saveModelFor("chat", model)
                        store.saveModelFor("image", imageModel)
                        showSettings = false
                    }) { Text("ذخیره", color = Gold) }
                },
                dismissButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showSettings = false; onSignOut() }) { Text("خروج از حساب", color = Color(0xFFFFA6A0)) }
                        TextButton(onClick = { showSettings = false }) { Text("انصراف") }
                    }
                }
            )
        }
    }
}


@Composable
private fun ThinkingOrb(
    diameter: androidx.compose.ui.unit.Dp = 118.dp,
    active: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val motion = rememberInfiniteTransition(label = "ema-glass-orb")
    val rotation by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 6800 else 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb-rotation"
    )
    val breathe by motion.animateFloat(
        initialValue = 0.965f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-breathe"
    )
    val ice = Color(0xFFBDEBFF)
    val cyan = Color(0xFF65D8F2)
    val silver = Color(0xFFE7F5FF)
    val lavender = Color(0xFF9BAEFF)
    val orbModifier = Modifier.size(diameter)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    Canvas(orbModifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.315f * breathe
        val halo = radius * 2.45f

        // Wide, feathered aura: several radial layers blend into the background.
        drawCircle(
            Brush.radialGradient(
                listOf(cyan.copy(alpha = 0.26f), ice.copy(alpha = 0.12f),
                    lavender.copy(alpha = 0.055f), Color.Transparent),
                c, halo
            ), halo, c
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color.Transparent, cyan.copy(alpha = 0.10f), Color.Transparent),
                c, radius * 2.05f
            ), radius * 2.05f, c
        )

        // Two rotating orbital ribbons make the movement visible at a glance.
        for (orbit in 0..1) {
            val path = Path()
            // Keep both orbital rings fully visible around the central orb.
            val rx = radius * (1.38f + orbit * 0.12f)
            val ry = radius * (0.72f + orbit * 0.12f)
            val direction = if (orbit == 0) 1f else -1f
            val angle = Math.toRadians((rotation * direction + orbit * 72f).toDouble())
            val ca = kotlin.math.cos(angle).toFloat()
            val sa = kotlin.math.sin(angle).toFloat()
            for (i in 0..160) {
                val t = i / 160f * (Math.PI * 2.0).toFloat()
                val px = kotlin.math.cos(t) * rx
                val py = kotlin.math.sin(t) * ry
                val x = c.x + px * ca - py * sa
                val y = c.y + px * sa + py * ca
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, cyan.copy(alpha = 0.20f), style = Stroke(width = 7.dp.toPx()))
            drawPath(
                path,
                Brush.sweepGradient(listOf(Color.Transparent, ice.copy(alpha = 0.86f),
                    cyan.copy(alpha = 0.72f), lavender.copy(alpha = 0.54f), Color.Transparent), c),
                style = Stroke(width = if (orbit == 0) 2.2.dp.toPx() else 1.35.dp.toPx())
            )
            val particleAngle = angle + (if (orbit == 0) 0.7 else 2.8)
            val px = kotlin.math.cos(particleAngle).toFloat() * rx
            val py = kotlin.math.sin(particleAngle).toFloat() * ry
            drawCircle(ice.copy(alpha = 0.92f), radius * 0.045f, Offset(c.x + px, c.y + py))
            drawCircle(cyan.copy(alpha = 0.26f), radius * 0.105f, Offset(c.x + px, c.y + py))
        }

        // Deep, translucent glass core with a cool luminous inner volume.
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFECFAFF), Color(0xFF8CDDF2), Color(0xFF416D9D),
                    Color(0xFF1C3153), Color(0xFF101B31)),
                Offset(c.x - radius * 0.34f, c.y - radius * 0.43f), radius * 1.62f
            ), radius, c
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x66FFFFFF), Color(0x338DEBFF),
                    Color(0x1495BFFF), Color.Transparent),
                Offset(c.x - radius * 0.28f, c.y - radius * 0.36f), radius * 1.18f
            ), radius * 0.96f, c
        )

        // Slow internal currents, rotating in opposite directions.
        for (strand in 0..2) {
            val path = Path()
            val phase = Math.toRadians((rotation * (if (strand == 1) -0.72f else 0.48f) + strand * 118f).toDouble()).toFloat()
            for (i in 0..180) {
                val t = i / 180f * (Math.PI * 2.0).toFloat()
                val x = c.x + kotlin.math.sin(t * 1.12f + phase) * radius * (0.70f - strand * 0.10f)
                val y = c.y + kotlin.math.sin(t * 2.0f + phase * 0.72f) *
                    kotlin.math.cos(t * 0.52f - phase) * radius * 0.66f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val tint = when (strand) { 0 -> ice; 1 -> cyan; else -> lavender }
            drawPath(path, tint.copy(alpha = 0.22f), style = Stroke(width = 5.dp.toPx()))
            drawPath(path, tint.copy(alpha = 0.84f), style = Stroke(width = 1.55.dp.toPx()))
        }

        // Re-draw the near halves of both orbital ribbons over the glass core.
        // The far halves remain behind it, creating a clear 3D wrap-around effect.
        for (orbit in 0..1) {
            val frontPath = Path()
            val rx = radius * (1.38f + orbit * 0.12f)
            val ry = radius * (0.72f + orbit * 0.12f)
            val direction = if (orbit == 0) 1f else -1f
            val angle = Math.toRadians((rotation * direction + orbit * 72f).toDouble())
            val ca = kotlin.math.cos(angle).toFloat()
            val sa = kotlin.math.sin(angle).toFloat()
            var drawingFront = false
            for (i in 0..160) {
                val t = i / 160f * (Math.PI * 2.0).toFloat()
                val px = kotlin.math.cos(t) * rx
                val py = kotlin.math.sin(t) * ry
                val x = c.x + px * ca - py * sa
                val y = c.y + px * sa + py * ca
                if (y >= c.y) {
                    if (!drawingFront) frontPath.moveTo(x, y) else frontPath.lineTo(x, y)
                    drawingFront = true
                } else {
                    drawingFront = false
                }
            }
            drawPath(frontPath, cyan.copy(alpha = 0.28f), style = Stroke(width = 7.dp.toPx()))
            drawPath(
                frontPath,
                Brush.sweepGradient(listOf(Color.Transparent, ice.copy(alpha = 0.92f),
                    cyan.copy(alpha = 0.82f), lavender.copy(alpha = 0.62f), Color.Transparent), c),
                style = Stroke(width = if (orbit == 0) 2.4.dp.toPx() else 1.6.dp.toPx())
            )
        }

        // Soft glass edge, not a hard neon outline.
        drawCircle(
            Brush.sweepGradient(listOf(Color.Transparent, ice.copy(alpha = 0.56f),
                cyan.copy(alpha = 0.72f), lavender.copy(alpha = 0.48f), Color.Transparent), c),
            radius * 1.005f, c, style = Stroke(width = 2.2.dp.toPx())
        )
        drawCircle(Color.White.copy(alpha = 0.78f), radius * 0.052f,
            Offset(c.x - radius * 0.40f, c.y - radius * 0.52f))
        drawCircle(Color.White.copy(alpha = 0.38f), radius * 0.022f,
            Offset(c.x + radius * 0.48f, c.y + radius * 0.19f))
        drawArc(
            Brush.sweepGradient(listOf(Color.Transparent, silver.copy(alpha = 0.72f),
                cyan.copy(alpha = 0.20f), Color.Transparent), c),
            startAngle = rotation * 0.75f + 205f,
            sweepAngle = 82f,
            useCenter = false,
            topLeft = Offset(c.x - radius * 0.84f, c.y - radius * 0.84f),
            size = androidx.compose.ui.geometry.Size(radius * 1.68f, radius * 1.68f),
            style = Stroke(width = 2.4.dp.toPx())
        )
    }
}

private fun playThinkingOrbSound(context: Context) {
    runCatching {
        val sampleRate = 44100
        val durationSeconds = 0.52
        val count = (sampleRate * durationSeconds).toInt()
        val pcm = ShortArray(count) { index ->
            val t = index.toDouble() / count
            val envelope = kotlin.math.sin(Math.PI * t).let { it * it }
            val glide = 310.0 + 360.0 * t
            val fundamental = kotlin.math.sin(2.0 * Math.PI * glide * t * durationSeconds)
            val overtone = kotlin.math.sin(2.0 * Math.PI * (glide * 1.51) * t * durationSeconds) * 0.28
            val shimmer = kotlin.math.sin(2.0 * Math.PI * 940.0 * t * durationSeconds) * 0.08
            ((fundamental + overtone + shimmer) * envelope * 4200.0).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.setVolume(0.42f)
        val written = track.write(pcm, 0, pcm.size)
        if (written > 0) {
            track.play()
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                    track.release()
                }
            }, 750L)
        } else track.release()
    }
}

private fun assistantActionPreview(action: AssistantAction): String {
    val p = action.parameters
    val amount = p["amount"]?.toLongOrNull()?.let { java.text.NumberFormat.getNumberInstance(java.util.Locale("fa", "IR")).format(it) + " تومان" }
    return when (action.module) {
        AssistantModule.FINANCE -> (if (action.operation == "add_income") "ثبت درآمد" else "ثبت هزینه") + ": " + (amount ?: "مبلغ نامشخص") + "، دسته: " + p["category"].orEmpty() + "، شرح: " + p["note"].orEmpty()
        AssistantModule.SPORTS -> "ثبت تمرین «" + p["title"].orEmpty() + "»؛ " + p["minutes"].orEmpty() + " دقیقه، " + p["distance"].orEmpty() + " کیلومتر"
        AssistantModule.HEALTH -> if (action.operation == "add_food") "ثبت غذا: " + p["food"].orEmpty() + "، " + p["grams"].orEmpty() + " گرم" else "نمایش دفتر سلامت"
        AssistantModule.TRAVEL -> "ثبت هزینهٔ سفر: " + (amount ?: "مبلغ نامشخص") + "، عنوان: " + p["title"].orEmpty() + "، پرداخت‌کننده: " + p["payer"].orEmpty()
        AssistantModule.MEMORY -> "ذخیره در حافظه: «" + p["note"].orEmpty() + "»"
        AssistantModule.JOURNAL -> if (action.operation == "add_entry") "ثبت در روزنگار: " + p["category"].orEmpty() + " — " + p["detail"].orEmpty() else "نمایش روزنگار: " + p["period"].orEmpty()
    }
}

private data class GuidedTourItem(val target: String, val title: String, val description: String)

private val guidedTourItems = listOf(
    GuidedTourItem("گوی هوشمند", "گفت‌وگو و درک درخواست", "سؤال بپرس، درباره روزت حرف بزن یا تصویر بفرست. دستیار موضوع را تشخیص می‌دهد و از ابزارهای متصل کمک می‌گیرد."),
    GuidedTourItem("میکروفون", "صحبت کن، نه فقط تایپ", "با لمس میکروفون گفتارت به متن تبدیل می‌شود. میکروفون فقط با انتخاب تو فعال می‌شود و شنود دائمی وجود ندارد."),
    GuidedTourItem("منوی فضاها", "ابزار مناسب هر موضوع", "ورزش، تغذیه، مالی، سفر، اتاق فکر، حافظه و بقیه فضاها را از اینجا باز کن. برای نمونه، می‌توانی یک روایت روزانه بگویی تا بخش‌های مختلفش در روزنگار دسته‌بندی شوند."),
    GuidedTourItem("تأیید و گزارش", "اطلاعاتت را مرور کن", "پیش از عملیات مهم، جزئیات را بررسی و تأیید کن. از «روایت هفته و ماه» جمع‌بندی داده‌های ثبت‌شده را ببین؛ دکمه راهنما هم همیشه برای مرور دوباره در دسترس است.")
)


@Composable
private fun GuidedTourOverlay(step: Int, targetBounds: Rect, onPrevious: () -> Unit, onNext: () -> Unit) {
    val item = guidedTourItems[step.coerceIn(guidedTourItems.indices)]
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawRect(Color(0x88070B12))
                if (targetBounds != Rect.Zero) {
                    val pad = 9.dp.toPx()
                    val left = targetBounds.left - pad
                    val top = targetBounds.top - pad
                    val width = targetBounds.width + pad * 2
                    val height = targetBounds.height + pad * 2
                    drawRoundRect(Color.Transparent, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()), blendMode = BlendMode.Clear)
                    drawRoundRect(Color(0xFFBFD2FF), Offset(left, top), androidx.compose.ui.geometry.Size(width, height), androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
            }
            Card(Modifier.align(Alignment.Center).fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 18.dp).heightIn(max = 560.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SupraGlass.Glass), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF66758D).copy(alpha = 0.65f))) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("آموزش سریع برنامه", color = Color(0xFFDCE3ED), fontWeight = FontWeight.Bold)
                        Text(listOf("مرحله یک از چهار", "مرحله دو از چهار", "مرحله سه از چهار", "مرحله چهار از چهار")[step.coerceIn(0, 3)], color = Color(0xFF9EACC0), style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(progress = { (step + 1).toFloat() / guidedTourItems.size }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Color(0xFF9CB9F4), trackColor = Color(0xFF303847))
                    Text(item.target, color = Color(0xFFBFD2FF), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(item.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(item.description, color = Color(0xFFB8C2CE), style = MaterialTheme.typography.bodySmall, lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (step > 0) OutlinedButton(onClick = onPrevious, modifier = Modifier.weight(1f)) { Text("قبلی") }
                        Button(onClick = onNext, modifier = Modifier.weight(1.5f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCE3ED), contentColor = Color(0xFF111722)), shape = RoundedCornerShape(14.dp)) {
                            Text(if (step == guidedTourItems.lastIndex) "پایان و ورود" else "بعدی")
                        }
                    }
                }
            }
        }
    }
}

