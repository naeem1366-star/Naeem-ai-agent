package com.naeem.ai
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@Composable
fun ProductLensDashboard(onClose: () -> Unit, gatewayUrl: String, accessToken: String) {
 val context=LocalContext.current
 val uriHandler=LocalUriHandler.current
 val scope= rememberCoroutineScope()
 var bitmap by remember { mutableStateOf<Bitmap?>(null) }
 var status by remember { mutableStateOf("از محصول عکس بگیر یا تصویرش را از گالری انتخاب کن.") }
 var loading by remember { mutableStateOf(false) }
 var productName by remember { mutableStateOf("") }
 var brand by remember { mutableStateOf("") }
 var category by remember { mutableStateOf("") }
 var description by remember { mutableStateOf("") }
 var searchQuery by remember { mutableStateOf("") }
 var findings by remember { mutableStateOf<List<WebFinding>>(emptyList()) }
 val client= remember { OkHttpClient.Builder().connectTimeout(25,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).build() }
 fun loadImage(uri:Uri) { scope.launch { runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } }.onSuccess { bitmap=it; findings=emptyList(); productName=""; status=if(it==null) "خواندن تصویر ممکن نشد." else "تصویر آماده است؛ محصول را شناسایی کن." }.onFailure { status=it.message ?: "باز کردن تصویر ناموفق بود." } } }
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::loadImage) }
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { image -> if(image!=null){bitmap=image;findings=emptyList();productName="";status="عکس گرفته شد؛ آماده شناسایی است."}else status="عکسی ثبت نشد." }
 fun identify() {
  val photo=bitmap ?: return
  if(accessToken.isBlank()){status="برای استفاده از جست‌وجوی هوشمند وارد حساب شو.";return}
  loading=true;findings=emptyList();status="در حال تشخیص محصول از روی تصویر…"
  scope.launch {
   try {
    val data=withContext(Dispatchers.IO) {
     val scale=minOf(1f,1280f/maxOf(photo.width,photo.height))
     val scaled=if(scale<1f) Bitmap.createScaledBitmap(photo,(photo.width*scale).toInt(),(photo.height*scale).toInt(),true) else photo
     val out=ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,82,out)
     "data:image/jpeg;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP)
    }
    val body=JSONObject().put("image",data).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request=Request.Builder().url(gatewayUrl.trimEnd('/')+"/v1/vision/product").header("apikey",EmaSupabaseConfig.PUBLISHABLE_KEY).header("Authorization","Bearer "+accessToken).post(body).build()
    val raw=withContext(Dispatchers.IO){client.newCall(request).execute().use { response ->
     val text=response.body?.string().orEmpty()
     if(!response.isSuccessful) throw IllegalStateException(runCatching{JSONObject(text).optJSONObject("error")?.optString("message")}.getOrNull() ?: "خطای سرور "+response.code)
     JSONObject(text).optString("identification")
    }}
    val start=raw.indexOf('{');val parsed=JSONObject(raw.substring(if(start>=0) start else 0))
    productName=parsed.optString("product_name");brand=parsed.optString("brand");category=parsed.optString("category");description=parsed.optString("visual_description")
    searchQuery=parsed.optString("search_query").ifBlank{listOf(brand,productName).filter{it.isNotBlank()}.joinToString(" ")}
    if(productName.isBlank()) throw IllegalStateException("محصول دقیق تشخیص داده نشد؛ عکس واضح‌تری بگیر.")
    status="محصول شناسایی شد؛ در حال جست‌وجوی نمونه‌ها و فروشگاه‌ها…"
    findings=DeepSearchClient().search(searchQuery,8)
    status=if(findings.isEmpty()) "محصول شناسایی شد، اما نتیجه وب پیدا نشد." else findings.size.toString()+" نتیجه وب پیدا شد؛ قیمت و مدل را بررسی کن."
   }catch(e:Exception){status=e.message ?: "در شناسایی محصول خطایی رخ داد."}finally{loading=false}
  }
 }
 Column(Modifier.fillMaxSize().background(Color(0xFF07111F)).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onClose){Icon(Icons.Default.ArrowBack,"بازگشت",tint=Color.White)}
   Column(Modifier.weight(1f)){Text("جست‌وجوی تصویری ایما",color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge);Text("مثل لنز؛ عکس بگیر و محصول را پیدا کن",color=Color(0xFF9EACBD),style=MaterialTheme.typography.labelSmall)}
   Icon(Icons.Default.ImageSearch,null,tint=Color(0xFF9CB9F4),modifier=Modifier.size(28.dp))
  }
  Surface(color=Color(0xFF111D2C),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){
   Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    if(bitmap!=null) Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxWidth().heightIn(max=300.dp),contentScale=ContentScale.Fit)
    else Box(Modifier.fillMaxWidth().height(210.dp).background(Color(0xFF172438),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.ImageSearch,null,tint=Color(0xFF9CB9F4),modifier=Modifier.size(48.dp));Text("عکس محصول را انتخاب کن",color=Color(0xFFB7C4D6))}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
     Button(onClick={camera.launch(null)},modifier=Modifier.weight(1f),enabled=!loading){Icon(Icons.Default.CameraAlt,null);Spacer(Modifier.width(5.dp));Text("عکس گرفتن")}
     OutlinedButton(onClick={gallery.launch(arrayOf("image/*"))},modifier=Modifier.weight(1f),enabled=!loading){Icon(Icons.Default.PhotoLibrary,null);Spacer(Modifier.width(5.dp));Text("گالری")}
    }
    Button(onClick=::identify,modifier=Modifier.fillMaxWidth(),enabled=bitmap!=null&&!loading){Icon(Icons.Default.Search,null);Spacer(Modifier.width(6.dp));Text("شناسایی و جست‌وجوی محصول")}
    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(status,color=Color(0xFFB7C4D6),style=MaterialTheme.typography.bodySmall)
   }
  }
  if(productName.isNotBlank()) Surface(color=Color(0xFF111D2C),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){
   Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    Text("محصول شناسایی‌شده",color=Color(0xFF9CB9F4),fontWeight=FontWeight.Bold);Text(productName,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
    if(brand.isNotBlank())Text("برند: "+brand,color=Color(0xFFE0E7F0))
    if(category.isNotBlank())Text("دسته‌بندی: "+category,color=Color(0xFFE0E7F0))
    if(description.isNotBlank())Text(description,color=Color(0xFF9EACBD),style=MaterialTheme.typography.bodySmall)
    Text("جست‌وجو: "+searchQuery,color=Color(0xFF9CB9F4),style=MaterialTheme.typography.labelSmall)
   }
  }
  if(findings.isNotEmpty()){
   Text("نتایج وب و فروشگاه‌ها",color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
   findings.forEach{item->Surface(color=Color(0xFF111D2C),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().clickable{runCatching{uriHandler.openUri(item.url)}}){Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(item.title,color=Color(0xFFEAF1FC),fontWeight=FontWeight.SemiBold);if(item.snippet.isNotBlank())Text(item.snippet,color=Color(0xFF9EACBD),style=MaterialTheme.typography.bodySmall,maxLines=3);Text(item.url,color=Color(0xFF9CB9F4),style=MaterialTheme.typography.labelSmall,maxLines=1)}}}
  }
  Text("تشخیص برند و مدل ممکن است خطا داشته باشد؛ قیمت و موجودی را در سایت فروشنده بررسی کن.",color=Color(0xFF8191A6),style=MaterialTheme.typography.labelSmall)
 }
}
