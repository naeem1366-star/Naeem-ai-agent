package com.naeem.ai

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.net.Uri

private data class CinemaProvider(
    val name: String,
    val description: String,
    val url: String,
    val region: String,
    val note: String
)

private val cinemaProviders = listOf(
    CinemaProvider("Internet Archive", "فیلم‌های کلاسیک، مستقل و آثار آزاد برای تماشای رایگان", "https://archive.org/details/movies", "بین‌المللی", "بدون حساب؛ آرشیو آثار آزاد و عمومی"),
    CinemaProvider("Netzkino", "مجموعه‌ای از فیلم‌های رایگان با پشتیبانی تبلیغات", "https://www.netzkino.de/", "آلمان و برخی مناطق", "بدون ثبت‌نام؛ دسترسی محتوا منطقه‌ای است"),
    CinemaProvider("Parsimondo · سینمای فارسی", "فیلم و سریال ایرانی از کانال‌های رسمی صاحبان حق پخش", "https://parsimondo.de/fa/", "بین‌المللی", "بیش از ۳۲۰۰ عنوان رایگان طبق معرفی سرویس؛ بدون ثبت‌نام"),
    CinemaProvider("Pluto TV", "کانال‌های زنده و فیلم‌های درخواستی رایگان", "https://pluto.tv/", "بسته به کشور", "رایگان با تبلیغات؛ فهرست کشورها متفاوت است"),
    CinemaProvider("ARTE", "سینمای هنری، مستند و سریال‌های منتخب", "https://www.arte.tv/", "اروپا و برخی مناطق", "بسیاری از محتواها بدون حساب؛ محدودیت پخش منطقه‌ای"),
    CinemaProvider("ZDF Mediathek", "فیلم، سریال، مستند و برنامه‌های تلویزیونی", "https://www.zdf.de/", "آلمان", "بدون حساب؛ بعضی آثار فقط در آلمان"),
    CinemaProvider("ARD Mediathek", "فیلم‌ها، سریال‌ها و تولیدات شبکه‌های عمومی آلمان", "https://www.ardmediathek.de/", "آلمان", "بدون حساب؛ برخی محتواها محدودیت جغرافیایی دارند"),
    CinemaProvider("YouTube · کانال‌های رسمی", "فیلم‌های رایگان منتشرشده توسط صاحبان حق پخش", "https://www.youtube.com/@Netzkino", "بین‌المللی", "بدون حساب برای تماشای عمومی؛ فقط کانال‌های رسمی و مجاز")
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CinemaDashboard(onClose: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<CinemaProvider?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var pageError by remember { mutableStateOf("") }
    var customViewActive by remember { mutableStateOf(false) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun leavePlayer() {
        val view = webView
        if (customViewActive) {
            view?.evaluateJavascript("document.exitFullscreen && document.exitFullscreen()", null)
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            customViewActive = false
        } else if (view?.canGoBack() == true) {
            view.goBack()
        } else {
            selected = null
            pageError = ""
        }
    }

    BackHandler(enabled = selected != null) { leavePlayer() }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF080D16)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selected != null) leavePlayer() else onClose() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(if (selected == null) "سینمای ایما" else selected?.name.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(if (selected == null) "فیلم و سریال رایگان، بدون ساخت حساب" else pageTitle.ifBlank { "پخش درون‌برنامه‌ای" }, color = Color(0xFF9BA9BC), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF9CC8F5), modifier = Modifier.size(27.dp))
        }

        if (selected == null) {
            Surface(color = Color(0xFF142235), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("تماشای رایگان داخل ایما", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("یکی از سرویس‌ها را انتخاب کن. صفحه در همین برنامه باز می‌شود؛ اگر سرویس اجازه پخش در WebView را بدهد، ویدیو با کنترل تمام‌صفحه خودش اجرا می‌شود.", color = Color(0xFFB8C5D6), style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cinemaProviders.forEach { provider ->
                    Surface(
                        color = Color(0xFF121B29),
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF29384B)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = provider
                            pageTitle = provider.name
                            pageError = ""
                        }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(color = Color(0xFF20334A), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFB8D9FA), modifier = Modifier.padding(10.dp).size(26.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(provider.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(provider.description, color = Color(0xFFB7C2D0), style = MaterialTheme.typography.bodySmall)
                                Text(provider.region + " · " + provider.note, color = Color(0xFF8EA5BE), style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = "باز کردن", tint = Color(0xFF9CC8F5))
                        }
                    }
                }
                Text("محتوای رایگان به کشور و مجوز پخش هر عنوان بستگی دارد. فیلم‌های جدید و آثار دارای حق پخش معمولاً رایگان و بدون حساب در دسترس نیستند.", color = Color(0xFF8D9BAD), style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CC8F5))
                Text(if (loading) "در حال بارگذاری سرویس…" else "برای پخش، فیلم یا قسمت موردنظرت را در سایت انتخاب کن.", color = Color(0xFFB7C2D0), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selected!!.url))) }
                }) { Icon(Icons.Default.OpenInBrowser, contentDescription = "باز کردن در مرورگر", tint = Color(0xFF9CC8F5)) }
            }
            if (pageError.isNotBlank()) Text(pageError, color = Color(0xFFFFB4A9), style = MaterialTheme.typography.bodySmall)
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val root = FrameLayout(ctx).apply { setBackgroundColor(AndroidColor.BLACK) }
                    val browser = WebView(ctx).apply {
                        setBackgroundColor(AndroidColor.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url ?: return false
                                return target.scheme !in listOf("http", "https")
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                loading = true
                                pageError = ""
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: selected?.name.orEmpty()
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                if (request?.isForMainFrame == true) {
                                    loading = false
                                    pageError = "این سرویس درون‌برنامه‌ای در دسترس نیست. از دکمه مرورگر استفاده کن یا سرویس دیگری را انتخاب کن."
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (view == null) return
                                customViewCallback = callback
                                customViewActive = true
                                (view.parent as? ViewGroup)?.removeView(view)
                                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                                this@apply.visibility = View.GONE
                                view.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
                            }
                            override fun onHideCustomView() {
                                val child = if (root.childCount > 1) root.getChildAt(root.childCount - 1) else null
                                child?.let { root.removeView(it) }
                                this@apply.visibility = View.VISIBLE
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null
                                customViewActive = false
                            }
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) pageTitle = title
                            }
                        }
                    }
                    webView = browser
                    root.addView(browser, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    browser.loadUrl(selected!!.url)
                    root
                },
                update = { }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.runCatching {
                stopLoading()
                webChromeClient = null
                webViewClient = WebViewClient()
                destroy()
            }
            webView = null
        }
    }
}
