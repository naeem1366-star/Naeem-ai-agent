package com.naeem.ai

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val FG=Color(0xFFD4D9E0)
private val FB=Color(0xFF111318)
private val FC=Color(0xFF20232B)
private val FM=Color(0xFFA8ADB8)

@Composable
fun FeatureHub(
    onClose:()->Unit,onFinance:()->Unit,onHealth:()->Unit,onGames:()->Unit,
    onDiscover:()->Unit,onMusic:()->Unit,onMemory:()->Unit,onAlbum:()->Unit,
    onSettings:()->Unit,onTravel:()->Unit,onSport:()->Unit,onMarkets:()->Unit,onWrapped:()->Unit,onTrail:()->Unit,onSocial:()->Unit
){
    var query by remember { mutableStateOf("") }
    var showFeatureMap by remember { mutableStateOf(false) }
    val carouselState = rememberLazyListState()
    if (showFeatureMap) {
        FeatureMapTree(onClose = { showFeatureMap = false })
        return
    }
    data class Entry(
        val title:String,val subtitle:String,val detail:String,
        val aiRole:String,val aiStatus:String,
        val icon:androidx.compose.ui.graphics.vector.ImageVector,
        val action:()->Unit,val keywords:String
    )
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }
    val entries=listOf(
        Entry("سفر و هم‌سفرها","برنامه‌ریزی سفر","سفرها، همراهان و مخارج","در گفتگو مقصد، تاریخ، همراهان یا هزینه‌ها را بگو. مغز هوشمند جزئیات را از حرف‌هایت استخراج می‌کند، اطلاعات ناقص را می‌پرسد و برای ثبت یا تغییر، خلاصه را به تأییدت می‌رساند.","اتصال دستیار: فعال",Icons.Default.Luggage,onTravel,"سفر مسافرت مقصد همسفر هزینه"),
        Entry("Naeem Trail","ناوبری طبیعت‌گردی","GPS، ثبت مسیر و فایل نقشه آفلاین","مسیر و مقصد طبیعت‌گردی را در گفتگو تعریف می‌کنی؛ هدف این است که مغز هوشمند برنامه و نکات مسیر را به این بخش وصل کند. ثبت و تحلیل خودکار مسیر و نقشه آفلاین هنوز کامل نشده‌اند.","اتصال دستیار: در حال توسعه",Icons.Default.Terrain,onTrail,"طبیعت گردی کوه پیمایی مسیر جی پی اس نقشه آفلاین پیمایش"),
        Entry("باشگاه ورزش","تمرین و آمادگی","تمرین، برنامه و رکوردها","تمرینت را با متن یا گفتار تعریف کن؛ مغز هوشمند نوع تمرین، مدت و مسافت را تشخیص می‌دهد، اگر چیزی مبهم باشد می‌پرسد و پس از تأیید، آن را در سوابق ورزشی ثبت می‌کند.","اتصال دستیار: فعال",Icons.Default.FitnessCenter,onSport,"ورزش تمرین قدرتی باشگاه فیتنس دویدن"),
        Entry("نبض بازار","قیمت دارایی‌ها","طلا، ارز و بازارهای جهانی","نام دارایی یا پرسشت را در گفتگو مطرح کن؛ مغز هوشمند می‌تواند داده‌های در دسترس را توضیح دهد و مقایسه کند. به‌روز بودن قیمت به اتصال منبع زنده و معتبر وابسته است.","تحلیل با دستیار: از طریق گفتگو",Icons.Default.TrendingUp,onMarkets,"بازار قیمت دلار طلا نقره بیت کوین رمزارز بورس ارز"),
        Entry("اخبار مهم","خبرهای مهم ایران و جهان","تیترهای مهم، فوری و اثرگذار","مغز هوشمند باید خبرهای مهم را از منابع معتبر گردآوری کند، خبرهای تکراری را کنار بگذارد، آن‌ها را بر اساس موضوع و میزان اهمیت مرتب کند و خلاصه‌ای روشن همراه با منبع و زمان انتشار ارائه دهد. می‌توانی درباره هر خبر سؤال تکمیلی بپرسی.","بخش خبری و دریافت زنده خبر: هنوز پیاده‌سازی نشده",Icons.Default.Newspaper,{},"اخبار مهم خبر فوری ایران جهان سیاست اقتصاد فناوری جنگ بحران رویداد")
        Entry("دفتر مالی","مدیریت پول","درآمد، هزینه و گزارش مالی","فقط در گفتگو بگو چه مبلغی خرج کردی یا درآمد داشتی؛ مغز هوشمند مبلغ، تاریخ و دسته را درمی‌آورد، ابهام را می‌پرسد و پس از تأییدت در دفتر مالی ثبت می‌کند.","اتصال دستیار: فعال",Icons.Default.AccountBalanceWallet,onFinance,"مالی پول هزینه درآمد"),
        Entry("روایت ماه","گزارش ماهانه","خلاصه عملکرد و اتفاق‌های ماه","مغز هوشمند اطلاعات ثبت‌شده در بخش‌های مختلف را کنار هم می‌گذارد و از آن‌ها گزارش ماهانه می‌سازد؛ هرچه ثبت‌ها کامل‌تر باشند، جمع‌بندی دقیق‌تر است.","گزارش از داده‌های ذخیره‌شده",Icons.Default.AutoAwesome,onWrapped,"گزارش ماهانه جمع‌بندی عملکرد"),
        Entry("سلامت و تغذیه","بدن، تغذیه و مراقبت‌های روزانه","غذا، کالری، چرخه قاعدگی و داروها","در گفتگو بگو چه خورده‌ای، چه فعالیتی داشته‌ای یا چه دارویی را در چه ساعتی مصرف می‌کنی؛ مغز هوشمند باید اطلاعات را تشخیص دهد و در دفتر سلامت ثبت کند. این فضا همچنین برای تقویم چرخه قاعدگی، ثبت علائم و یادآوری زمان دارو در نظر گرفته شده است. ثبت خودکار از گفتگو، تقویم چرخه و یادآور دارو هنوز پیاده‌سازی نشده‌اند.","اتصال کامل سلامت به دستیار: در حال توسعه",Icons.Default.Favorite,onHealth,"سلامت تغذیه کالری غذا"),
        Entry("حافظه هوشمند","یادداشت‌های من","نکته‌ها و اطلاعات مهم","وقتی در گفتگو می‌گویی «این را یادت بماند»، مغز هوشمند نکته را آماده می‌کند، برای تأیید نشان می‌دهد و پس از اجازه‌ات ذخیره می‌کند تا در گفتگوهای بعدی به کار بیاید.","اتصال دستیار: فعال",Icons.Default.Bookmark,onMemory,"حافظه یادداشت"),
        Entry("آلبوم تصاویر","آرشیو آثار","تصاویر ساخته‌شده","در گفتگو تصویر را توصیف یا اصلاح کن؛ مغز هوشمند درخواست را برای ابزار تصویر آماده می‌کند. تصاویر ساخته‌شده در آلبوم نگهداری می‌شوند و مدیریت آرشیو از همین بخش است.","ابزار تصویر متصل؛ مدیریت آرشیو در همین بخش",Icons.Default.Collections,onAlbum,"آلبوم تصویر عکس"),
        Entry("بازی‌ها","چالش و سرگرمی","پانتومیم و تجربه‌های هوشمند آینده","در بازی‌های هوشمند، مغز گفتگو می‌تواند نقش را توضیح دهد، معما بسازد و روند بازی را هدایت کند. فعلاً پانتومیم فعال است و بازی‌های داستانی دیگر هنوز در حال توسعه‌اند.","بازی‌های متصل به AI: در حال توسعه",Icons.Default.SportsEsports,onGames,"بازی سرگرمی پانتومیم"),
        Entry("سینما","فیلم و پوستر","کشف فیلم و پیشنهاد تماشا","در گفتگو سلیقه یا حال‌وهوایت را بگو؛ مغز هوشمند فیلم پیشنهاد می‌دهد، بدون اسپویل معرفی می‌کند یا درباره سبک و داستانش تحلیل می‌نویسد. جست‌وجو و آرشیو در فضای سینماست.","پیشنهاد و تحلیل با دستیار: فعال",Icons.Default.Movie,onDiscover,"فیلم سینما"),
        Entry("موسیقی","آهنگ و هنرمند","جست‌وجو و کشف موسیقی","حال‌وهوا، سبک یا هنرمند موردعلاقه‌ات را در گفتگو بگو؛ مغز هوشمند پیشنهاد می‌دهد و جست‌وجو و پیش‌نمایش موسیقی در همین بخش انجام می‌شود.","پیشنهاد از دستیار: از طریق گفتگو",Icons.Default.MusicNote,onMusic,"موسیقی آهنگ"),
        Entry("تحلیل اینستاگرام","رشد و عملکرد پیج","اتصال حساب و تحلیل محتوا","آمار پیج را وارد کن تا مغز هوشمند نرخ تعامل، بازدید و عملکرد محتوا را بررسی و برای رشد پیشنهاد بدهد. تحلیل خودکار حساب پس از اتصال رسمی و دریافت مجوزهای Meta ممکن می‌شود.","تحلیل دستی: آماده؛ اتصال رسمی: نیازمند تنظیم Meta",Icons.Default.Insights,onSocial,"اینستاگرام شبکه اجتماعی تحلیل پیج رشد محتوا") ,
        Entry("تنظیمات","اتصال و حریم خصوصی","مدیریت اتصال و امنیت","از اینجا مدل هوش مصنوعی، نشانی سرویس و کلید API را تنظیم می‌کنی؛ این اتصال، مسیر ارتباط مغز هوشمند با گفتگو و ابزارهای برنامه است.","مرکز تنظیمات مغز هوشمند",Icons.Default.Settings,onSettings,"تنظیمات اتصال")
    )
    val filtered=entries.filter{query.isBlank()||it.title.contains(query,true)||it.subtitle.contains(query,true)||it.keywords.contains(query,true)}
    val gold=Color(0xFFD4D9E0)
    val champagne=Color(0xFFE8ECF0)
    val hubBg=Color(0xFF090B0E)
    selectedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            containerColor = Color(0xFF171C23),
            titleContentColor = Color(0xFFE0E6EE),
            textContentColor = Color(0xFFB8C2CE),
            title = { Text(entry.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entry.detail, style = MaterialTheme.typography.bodyMedium)
                    Divider(color = Color(0xFF3B4551))
                    Text("ارتباط با مغز هوشمند", color = Color(0xFFE0E6EE), fontWeight = FontWeight.SemiBold)
                    Text(entry.aiRole, style = MaterialTheme.typography.bodySmall)
                    Surface(color = Color(0xFF252D37), shape = RoundedCornerShape(10.dp)) {
                        Text(entry.aiStatus, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Color(0xFFD4DCE6), style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEntry = null; entry.action() }) {
                    Text("ورود به این بخش", color = Color(0xFFE0E6EE))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEntry = null }) { Text("بستن", color = Color(0xFF9BA7B5)) }
            }
        )
    }
    Column(Modifier.fillMaxSize().background(hubBg.copy(alpha=0.84f)).padding(horizontal=18.dp, vertical=14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("NAEEM AI  /  SPACES",color=gold.copy(alpha=.82f),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Text("فضاهای من",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("هر فضا را با انتخاب آیکون خودش باز کن.",color=Color(0xFFAAB2BD),style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick={showFeatureMap=true}){Icon(Icons.Default.AccountTree,null,tint=gold)}
            IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=gold)}
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF171C23),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF46515F))
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Icon(Icons.Default.Hub, null, tint = Color(0xFFE0E6EE), modifier = Modifier.size(27.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("مغز هوشمند Naeem AI", color = Color(0xFFE0E6EE), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("وقتی خودت گفتگو یا میکروفون را فعال می‌کنی، حرف‌هایت را می‌فهمد، موضوع را تشخیص می‌دهد و اطلاعات را به بخش مرتبط می‌فرستد؛ ثبت یا تغییر اطلاعات مهم با تأیید تو انجام می‌شود.", color = Color(0xFFADB7C4), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
            shape=RoundedCornerShape(16.dp),placeholder={Text("جست‌وجو در فضاها…")},
            leadingIcon={Icon(Icons.Default.Search,null,tint=gold)},
            colors=OutlinedTextFieldDefaults.colors(
                focusedBorderColor=gold,unfocusedBorderColor=Color(0xFF454D58),
                focusedContainerColor=Color(0xFF191D22),unfocusedContainerColor=Color(0xFF191D22),
                cursorColor=gold
            )
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(if(query.isBlank()) "گالری قابلیت‌ها" else "نتایج جست‌وجو",color=champagne,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(if(query.isBlank()) "←  کشیدن برای مرور  →" else "${filtered.size} مورد",color=Color(0xFF8E98A5),style=MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        if(filtered.isEmpty()){
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("قابلیتی با این نام پیدا نشد.",color=Color(0xFFAAB2BD))}
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                items(filtered.size) { index ->
                    val entry = filtered[index]
                    val accent = when (entry.title) {
                        "سلامت و تغذیه" -> Color(0xFF9AD6B0)
                        "باشگاه ورزش" -> Color(0xFF91BFE8)
                        "دفتر مالی", "نبض بازار" -> Color(0xFFD8BD83)
                        "سفر و هم‌سفرها", "Naeem Trail" -> Color(0xFFA8C99A)
                        "موسیقی" -> Color(0xFFC2A8E8)
                        "اخبار مهم" -> Color(0xFF8DBBD0)
                        "سینما" -> Color(0xFFD9A6B8)
                        "حافظه هوشمند" -> Color(0xFFB5B7D9)
                        "آلبوم تصاویر" -> Color(0xFFD5B49A)
                        "بازی‌ها" -> Color(0xFFE0A68E)
                        "روایت ماه" -> Color(0xFFB9C5D8)
                        "تحلیل اینستاگرام" -> Color(0xFFE0A0B8)
                        else -> Color(0xFFB8C2CE)
                    }
                    Column(
                        Modifier.fillMaxWidth().clickable(onClick = { selectedEntry = entry }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.size(88.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Offset graphite layers create a restrained 3D object, not a flat icon tile.
                            Box(
                                Modifier.fillMaxSize().offset(y = 5.dp)
                                    .background(Color(0xFF020304), RoundedCornerShape(22.dp))
                            )
                            Surface(
                                modifier = Modifier.fillMaxSize().offset(y = (-1).dp),
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xFF171C23),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
                                shadowElevation = 12.dp
                            ) {
                                Box(
                                    Modifier.fillMaxSize().background(
                                        ComposeBrush.linearGradient(
                                            listOf(accent.copy(alpha = 0.24f), Color(0xFF202731), Color(0xFF0B0E13))
                                        )
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.align(Alignment.TopCenter).padding(top = 1.dp)
                                            .width(42.dp).height(1.dp)
                                            .background(accent.copy(alpha = 0.72f), RoundedCornerShape(1.dp))
                                    )
                                    Icon(
                                        entry.icon,
                                        null,
                                        tint = accent,
                                        modifier = Modifier.size(39.dp)
                                    )
                                }
                            }
                        }
                        Text(entry.title, color = Color(0xFFD4D9E0), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                        IconButton(onClick = { selectedEntry = entry }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Info, contentDescription = "توضیحات " + entry.title, tint = Color(0xFF9BA7B5), modifier = Modifier.size(16.dp)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Swipe,null,tint=gold.copy(alpha=.7f),modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("برای ورود، آیکون هر بخش را لمس کن",color=Color(0xFF8E98A5),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
@Composable private fun FCard(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=FC),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
@Composable private fun FInput(label:String,value:String,change:(String)->Unit,modifier:Modifier=Modifier){OutlinedTextField(value,change,modifier=modifier,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=FG,unfocusedBorderColor=Color(0xFF454954)))}

private data class HLog(val name:String,val kcal:Int,val type:String,val at:Long,val protein:Double=0.0,val carbs:Double=0.0,val fat:Double=0.0)
private data class FoodInfo(val name:String,val kcal:Double,val protein:Double,val carbs:Double,val fat:Double)
private val foodBank=listOf(
 FoodInfo("سینه مرغ پخته",165.0,31.0,0.0,3.6),FoodInfo("تخم مرغ",155.0,13.0,1.1,11.0),
 FoodInfo("برنج سفید پخته",130.0,2.7,28.2,0.3),FoodInfo("نان سنگک",259.0,9.0,52.0,1.5),
 FoodInfo("ماست کم‌چرب",63.0,5.3,7.0,1.6),FoodInfo("موز",89.0,1.1,22.8,0.3),
 FoodInfo("سیب",52.0,0.3,13.8,0.2),FoodInfo("بادام",579.0,21.2,21.6,49.9),
 FoodInfo("عدس پخته",116.0,9.0,20.1,0.4),FoodInfo("تن ماهی آبکش‌شده",132.0,29.0,0.0,1.0),
 FoodInfo("شیر کم‌چرب",42.0,3.4,5.0,1.0),FoodInfo("سیب‌زمینی آب‌پز",87.0,1.9,20.1,0.1)
)
private class HStore(c:Context){
 private val p=c.getSharedPreferences("naeem_health_local",Context.MODE_PRIVATE)
 fun get(k:String,d:String="")=p.getString(k,d)?:d
 fun put(k:String,v:String){p.edit().putString(k,v).apply()}
 fun logs():List<HLog> = runCatching{val a=org.json.JSONArray(p.getString("logs","[]"));(0 until a.length()).mapNotNull{i->a.optJSONObject(i)?.let{HLog(it.optString("n"),it.optInt("c"),it.optString("t"),it.optLong("a"),it.optDouble("p"),it.optDouble("b"),it.optDouble("f"))}}}.getOrDefault(emptyList())
 fun add(x:HLog){val a=org.json.JSONArray();(logs()+x).takeLast(500).forEach{a.put(org.json.JSONObject().put("n",it.name).put("c",it.kcal).put("t",it.type).put("a",it.at).put("p",it.protein).put("b",it.carbs).put("f",it.fat))};p.edit().putString("logs",a.toString()).apply()}
 fun remove(at:Long){val a=org.json.JSONArray();logs().filterNot{it.at==at}.forEach{a.put(org.json.JSONObject().put("n",it.name).put("c",it.kcal).put("t",it.type).put("a",it.at))};p.edit().putString("logs",a.toString()).apply()}
}

private val HBg=Color(0xFF0A0C10)
private val HPanel=Color(0xFF1B2421)
private val HMint=Color(0xFF9AD6B0)
private val HMuted=Color(0xFF9AA3AE)
@Composable private fun HCard(content:@Composable ColumnScope.()->Unit){
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=HPanel),shape=RoundedCornerShape(22.dp),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp),content=content)
 }
}
@Composable private fun HInput(label:String,value:String,change:(String)->Unit,modifier:Modifier=Modifier){
 OutlinedTextField(value,change,modifier=modifier,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),shape=RoundedCornerShape(15.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=HMint,unfocusedBorderColor=Color(0xFF454D58),focusedContainerColor=Color(0xFF171B21),unfocusedContainerColor=Color(0xFF171B21),focusedLabelColor=HMint,cursorColor=HMint))
}
@Composable private fun HMetric(title:String,value:String,sub:String,modifier:Modifier){
 val icon=when(title){"BMI"->Icons.Default.Favorite;"کالری پایه"->Icons.Default.LocalFireDepartment;"هدف کالری"->Icons.Default.Flag;else->Icons.Default.Restaurant}
 Card(modifier,colors=CardDefaults.cardColors(containerColor=HPanel),shape=RoundedCornerShape(19.dp),elevation=CardDefaults.cardElevation(defaultElevation=2.dp)){
  Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){Icon(icon,null,tint=HMint,modifier=Modifier.size(16.dp));Text(title,color=HMuted,style=MaterialTheme.typography.labelSmall)}
   Text(value,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black)
   Text(sub,color=HMuted,style=MaterialTheme.typography.labelSmall)
  }
 }
}

@Composable fun HealthDashboard(onClose:()->Unit){
 val ctx=LocalContext.current;val store=remember{HStore(ctx)}
 var height by remember{mutableStateOf(store.get("h"))};var weight by remember{mutableStateOf(store.get("w"))};var age by remember{mutableStateOf(store.get("age"))}
 var sex by remember{mutableStateOf(store.get("sex","مرد"))};var activity by remember{mutableStateOf(store.get("activity","متوسط"))};var goal by remember{mutableStateOf(store.get("goal","حفظ وزن"))}
 var water by remember{mutableIntStateOf(store.get("water","0").toIntOrNull()?:0)}
 var name by remember{mutableStateOf("")};var kcal by remember{mutableStateOf("")};var logs by remember{mutableStateOf(store.logs())}
 var foodSearch by remember{mutableStateOf("")};var grams by remember{mutableStateOf("100")};var selectedFood by remember{mutableStateOf<FoodInfo?>(null)};var meal by remember{mutableStateOf("ناهار")}
 var walkKm by remember{mutableStateOf("2")};var walkMinutes by remember{mutableStateOf("30")}
 val h=height.toDoubleOrNull()?.div(100.0);val w=weight.toDoubleOrNull();val years=age.toIntOrNull()
 val bmi=if(h!=null&&h>0&&w!=null&&w>0)w/(h*h)else null
 val bmr=if(h!=null&&h>0&&w!=null&&w>0&&years!=null&&years>0)10*w+6.25*(h*100)-5*years+(if(sex=="مرد")5 else -161) else null
 val tdee=bmr?.times(when(activity){"کم‌تحرک"->1.2;"سبک"->1.375;"زیاد"->1.725;else->1.55})?.toInt()
 val target=tdee?.let{when(goal){"کاهش وزن"->it-350;"افزایش وزن"->it+300;else->it}}
 val today=java.util.Calendar.getInstance().apply{set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
 val todayLogs=logs.filter{it.at>=today};val eaten=todayLogs.filter{it.type.startsWith("غذا")}.sumOf{it.kcal};val burned=todayLogs.filter{it.type=="فعالیت"}.sumOf{it.kcal}
 val nf=remember{NumberFormat.getNumberInstance(Locale.US)}
 Column(Modifier.fillMaxSize().background(HBg).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   Box(Modifier.size(48.dp).background(Color(0xFF24372F),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Eco,null,tint=HMint,modifier=Modifier.size(29.dp))}
   Spacer(Modifier.width(11.dp))
   Column(Modifier.weight(1f)){Text("سلامت و تغذیه",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("داشبورد بدن و انرژی",color=HMuted,style=MaterialTheme.typography.bodySmall)}
   IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=HMint)}
  }
  Card(colors=CardDefaults.cardColors(containerColor=HPanel),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
   Box(Modifier.fillMaxWidth().background(ComposeBrush.linearGradient(listOf(Color(0xFF293C34),Color(0xFF1B2823),Color(0xFF202B27)))).padding(17.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
      Text("HEALTH SPACE",color=HMint,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
      Text("قدم‌به‌قدم، سالم‌تر",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)
      Text("غذا، فعالیت، چرخه قاعدگی و داروها را در یک پرونده دنبال کن؛ برنامه هفتگی و بیدارباش هوشمند هم در مسیر توسعه است.",color=Color(0xFFBFC6D0),style=MaterialTheme.typography.bodySmall)
     }
     Box(Modifier.size(68.dp).background(ComposeBrush.radialGradient(listOf(Color(0xFF9AD6B0),Color(0xFF426B56),Color.Transparent)),CircleShape),contentAlignment=Alignment.Center){
      Icon(Icons.Default.HealthAndSafety,null,tint=Color(0xFFDDF5E5),modifier=Modifier.size(37.dp))
     }
    }
   }
  }
  Spacer(Modifier.height(12.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(bottom=24.dp)){
   item{HCard{
    Text("پروفایل بدن",color=HMint,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){HInput("قد cm",height,{height=it.filter(Char::isDigit)},Modifier.weight(1f));HInput("وزن kg",weight,{weight=it.filter{c->c.isDigit()||c=='.'}},Modifier.weight(1f));HInput("سن",age,{age=it.filter(Char::isDigit)},Modifier.weight(1f))}
    Row{listOf("مرد","زن").forEach{FilterChip(selected=sex==it,onClick={sex=it},label={Text(it)})}}
    Text("فعالیت روزانه",color=FM);Row{listOf("کم‌تحرک","سبک","متوسط","زیاد").forEach{FilterChip(selected=activity==it,onClick={activity=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}
    Text("هدف",color=FM);Row{listOf("کاهش وزن","حفظ وزن","افزایش وزن").forEach{FilterChip(selected=goal==it,onClick={goal=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}
    Button(onClick={store.put("h",height);store.put("w",weight);store.put("age",age);store.put("sex",sex);store.put("activity",activity);store.put("goal",goal)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ذخیرهٔ پروفایل")}
   }}
   item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    HMetric("BMI",bmi?.let{String.format(Locale.US,"%.1f",it)}?:"—",bmi?.let{when{it<18.5->"کمبود وزن";it<25->"محدوده معمول";it<30->"اضافه‌وزن";else->"بالا"}}?:"قد و وزن را وارد کن",Modifier.weight(1f))
    HMetric("کالری پایه",bmr?.toInt()?.toString()?:"—","BMR روزانه",Modifier.weight(1f))
   };Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=7.dp)){HMetric("هدف کالری",target?.toString()?:"—","برآورد روزانه",Modifier.weight(1f));HMetric("مصرف امروز",eaten.toString(),"فعالیت: "+burned+" kcal",Modifier.weight(1f))}
    val pToday=todayLogs.filter{it.type.startsWith("غذا")}.sumOf{it.protein};val cToday=todayLogs.filter{it.type.startsWith("غذا")}.sumOf{it.carbs};val fToday=todayLogs.filter{it.type.startsWith("غذا")}.sumOf{it.fat}
   Text("درشت‌مغذی‌های امروز  •  پروتئین "+String.format(Locale.US,"%.1f",pToday)+"g  •  کربوهیدرات "+String.format(Locale.US,"%.1f",cToday)+"g  •  چربی "+String.format(Locale.US,"%.1f",fToday)+"g",color=HMint,style=MaterialTheme.typography.labelSmall)
   if(bmi!=null)Text("BMI فقط شاخص غربالگری است، نه تشخیص پزشکی.",color=FM,style=MaterialTheme.typography.labelSmall)
   }
   item{HCard{
    Text("بانک غذایی؛ محاسبه بر اساس گرم",color=HMint,fontWeight=FontWeight.Bold)
    OutlinedTextField(foodSearch,{foodSearch=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("جست‌وجوی ماده غذایی")},leadingIcon={Icon(Icons.Default.Search,null)})
    foodBank.filter{it.name.contains(foodSearch,true)}.forEach{food->TextButton(onClick={selectedFood=food}){Text(food.name+" • "+food.kcal.toInt()+" kcal / ۱۰۰ گرم",color=if(selectedFood==food)HMint else Color.White)}}
    if(selectedFood!=null){
     Text("انتخاب: "+selectedFood!!.name,color=HMint,fontWeight=FontWeight.SemiBold)
     HInput("مقدار به گرم",grams,{grams=it.filter{c->c.isDigit()||c=='.'}},Modifier.fillMaxWidth())
     Row{listOf("صبحانه","ناهار","شام","میان‌وعده").forEach{FilterChip(selected=meal==it,onClick={meal=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}
     val g=grams.toDoubleOrNull()?:0.0;val factor=g/100.0
     Text("انرژی: "+(selectedFood!!.kcal*factor).toInt()+" kcal • پروتئین: "+String.format(Locale.US,"%.1f",selectedFood!!.protein*factor)+" g",color=HMint)
     Text("کربوهیدرات: "+String.format(Locale.US,"%.1f",selectedFood!!.carbs*factor)+" g • چربی: "+String.format(Locale.US,"%.1f",selectedFood!!.fat*factor)+" g",color=FM,style=MaterialTheme.typography.bodySmall)
     Button(onClick={store.add(HLog(selectedFood!!.name,(selectedFood!!.kcal*factor).roundToInt(),"غذا • "+meal,System.currentTimeMillis(),selectedFood!!.protein*factor,selectedFood!!.carbs*factor,selectedFood!!.fat*factor));logs=store.logs();selectedFood=null;grams="100"},enabled=g>0,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ثبت در دفترچه غذایی")}
    }
   }}
   item{HCard{Text("ثبت خوراکی و کالری",color=HMint,fontWeight=FontWeight.Bold);HInput("نام غذا یا وعده",name,{name=it},Modifier.fillMaxWidth());HInput("کالری kcal",kcal,{kcal=it.filter(Char::isDigit)},Modifier.fillMaxWidth());Button(onClick={val c=kcal.toIntOrNull()?:0;if(name.isNotBlank()&&c>0){store.add(HLog(name,c,"غذا",System.currentTimeMillis()));logs=store.logs();name="";kcal=""}},enabled=name.isNotBlank()&&kcal.toIntOrNull()!=null,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ثبت وعده")};Text("برای خوراکی‌هایی که در بانک نیستند، کالری را می‌توانی دستی ثبت کنی.",color=FM,style=MaterialTheme.typography.labelSmall)}}
   item{HCard{Text("آب روزانه",color=HMint,fontWeight=FontWeight.Bold);Text(water.toString()+" از ۸ لیوان",color=Color.White,style=MaterialTheme.typography.titleLarge);LinearProgressIndicator(progress={(water/8f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=HMint,trackColor=HBg);Row{OutlinedButton(onClick={water=(water-1).coerceAtLeast(0);store.put("water",water.toString())}){Text("−۱")};Button(onClick={water++;store.put("water",water.toString())},colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("+ یک لیوان")}}}}
   item{HCard{
    var actName by remember{mutableStateOf("تمرین ورزشی")};var actKcal by remember{mutableStateOf("")}
    val walkEstimate=((walkKm.toDoubleOrNull()?:0.0)*(w?:0.0)*0.75).toInt()
    Text("فعالیت و پیاده‌روی",color=HMint,fontWeight=FontWeight.Bold)
    Text("برآورد پیاده‌روی بر اساس مسافت و وزن ثبت‌شده",color=FM,style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){HInput("مسافت km",walkKm,{walkKm=it.filter{c->c.isDigit()||c=='.'}},Modifier.weight(1f));HInput("مدت دقیقه",walkMinutes,{walkMinutes=it.filter(Char::isDigit)},Modifier.weight(1f))}
    Text("کالری تخمینی پیاده‌روی: "+walkEstimate+" kcal",color=HMint,fontWeight=FontWeight.Bold)
    Button(onClick={if(walkEstimate>0){store.add(HLog("پیاده‌روی "+walkKm+" کیلومتر • "+walkMinutes+" دقیقه",walkEstimate,"فعالیت",System.currentTimeMillis()));logs=store.logs()}},enabled=walkEstimate>0,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ثبت پیاده‌روی")}
    Divider(color=FB)
    Text("فعالیت دیگر",color=HMint,fontWeight=FontWeight.SemiBold)
    HInput("نوع فعالیت",actName,{actName=it},Modifier.fillMaxWidth());HInput("کالری سوزانده‌شده",actKcal,{actKcal=it.filter(Char::isDigit)},Modifier.fillMaxWidth())
    Button(onClick={val c=actKcal.toIntOrNull()?:0;if(c>0){store.add(HLog(actName,c,"فعالیت",System.currentTimeMillis()));logs=store.logs();actKcal=""}},enabled=actKcal.toIntOrNull()!=null,colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ثبت فعالیت")}
   }}
   item{HCard{Text("پیشنهاد تغذیه‌ای",color=HMint,fontWeight=FontWeight.Bold);val tips=when(goal){"کاهش وزن"->listOf("در هر وعده پروتئین و سبزیجات داشته باش.","نوشیدنی شیرین را با آب جایگزین کن.","کاهش کالری را تدریجی نگه دار.");"افزایش وزن"->listOf("از میان‌وعده‌های مغذی استفاده کن.","پروتئین را در وعده‌ها پخش کن.","افزایش انرژی را تدریجی انجام بده.");else->listOf("وعده‌ها را متعادل و متنوع نگه دار.","در طول روز آب کافی بنوش.","پروتئین، سبزیجات و غلات کامل را ترکیب کن.")};tips.forEach{Text("• "+it,color=Color.White,style=MaterialTheme.typography.bodySmall)}}}
   item{Text("ثبت‌های امروز",color=HMint,fontWeight=FontWeight.Bold)}
   items(todayLogs.reversed(),key={it.at}){log->Card(colors=CardDefaults.cardColors(containerColor=HPanel)){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(log.name,color=Color.White);Text(log.type,color=FM,style=MaterialTheme.typography.labelSmall)};Text(log.kcal.toString()+" kcal",color=HMint);IconButton(onClick={store.remove(log.at);logs=store.logs()}){Icon(Icons.Default.Delete,null,tint=Color(0xFFFF9A91))}}}}
  }
 }
}
@Composable private fun FMetric(title:String,value:String,sub:String,modifier:Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=FC),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(12.dp)){Text(title,color=FM,style=MaterialTheme.typography.labelSmall);Text(value,color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(sub,color=FM,style=MaterialTheme.typography.labelSmall)}}}

@Composable fun GamesHub(onClose:()->Unit){
 var seconds by remember{mutableIntStateOf(60)}
 var running by remember{mutableStateOf(false)}
 var team by remember{mutableIntStateOf(1)}
 var team1 by remember{mutableStateOf("تیم آفتاب")}
 var team2 by remember{mutableStateOf("تیم ماه")}
 var score1 by remember{mutableIntStateOf(0)}
 var score2 by remember{mutableIntStateOf(0)}
 var category by remember{mutableStateOf("ترکیبی")}
 var word by remember{mutableStateOf("برای شروع، کلمه را دریافت کن")}
 var round by remember{mutableIntStateOf(1)}
 var showWord by remember{mutableStateOf(false)}
 val decks=remember{mapOf("ترکیبی" to listOf("فضانورد","چتر نجات","آشپز","ترافیک","کارآگاه","کتابخانه","آتش‌نشان","ماهیگیری","ربات","سینما","دوچرخه","آبشار","جادوگر","بستنی","قطار","عکاس","دندان‌پزشک","کوه‌نوردی","بادبادک","خواب‌آلود"),"سینما" to listOf("کارگردان","فرش قرمز","اسکار","دوربین","فیلمنامه","اکران","جلوه‌های ویژه","بازیگر","سکانس"),"سخت" to listOf("پارادوکس","ناخودآگاه","جاذبه","معماری","دیپلماسی","فرسایش","کهکشان","نوستالژی","الگوریتم","توربین"))}
 LaunchedEffect(running,seconds){if(running&&seconds>0){kotlinx.coroutines.delay(1000);seconds=(seconds-1).coerceAtLeast(0);if(seconds<=1)running=false}}
 fun next(){word=decks[category].orEmpty().random();seconds=60;running=false;showWord=true;round++}
 Column(Modifier.fillMaxSize().background(Color(0xFF0A0C10).copy(alpha = 0.84f))){
  Row(Modifier.fillMaxWidth().background(Color(0xFF1B1F25)).padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("GAME STUDIO",color=Color(0xFFBFC6D0),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Text("باشگاه بازی",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("پانتومیم؛ فعلاً تنها بازی فعال این مجموعه",color=Color(0xFFBFC6D0),style=MaterialTheme.typography.bodySmall)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=Color.White)}}
  LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF20252D)),shape=RoundedCornerShape(26.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("دور " + round,color=Color(0xFFD4D9E0),fontWeight=FontWeight.Bold);Text("پانتومیم تیمی",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)};Surface(color=Color(0xFF303741),shape=CircleShape){Text("⏱",modifier=Modifier.padding(12.dp),color=Color.White)}}
    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("ترکیبی","سینما","سخت").forEach{FilterChip(selected=category==it,onClick={category=it},label={Text(it)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFFBFC6D0),selectedLabelColor=Color.White,containerColor=Color(0xFF20252D),labelColor=Color.White))}}
    Box(Modifier.fillMaxWidth().background(Color(0xFF0A0C10),RoundedCornerShape(22.dp)).padding(vertical=25.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Text("کلمهٔ مخصوص اجرا",color=Color(0xFFD4D9E0));Text(if(showWord)word else "برای نمایش کلمه بزن",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text(String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60),color=if(seconds<=10)Color(0xFFFF817D) else Color(0xFFD4D9E0),style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Black);Text(if(seconds==0)"زمان تمام شد!" else "زمان باقی‌مانده",color=Color(0xFFBFC6D0),style=MaterialTheme.typography.labelSmall)}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={if(!showWord){word=decks[category].orEmpty().random();showWord=true};running=!running},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFBFC6D0)),shape=RoundedCornerShape(14.dp)){Icon(if(running)Icons.Default.Pause else Icons.Default.PlayArrow,null);Text(if(running)"مکث" else "شروع زمان‌سنج")};OutlinedButton(onClick={next()},modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.SkipNext,null);Text("کلمه بعدی")}}
    Button(onClick={if(team==1)score1++ else score2++;next()},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFD4D9E0),contentColor=Color(0xFF171B21)),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.CheckCircle,null);Text("حدس درست بود! +۱")}
   }}}
   item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF20252D)),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("جدول امتیاز",color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){listOf(Triple(team1,score1,1),Triple(team2,score2,2)).forEach{entry->Card(Modifier.weight(1f).clickable{team=entry.third},colors=CardDefaults.cardColors(containerColor=if(team==entry.third)Color(0xFF303741) else Color(0xFF20252D)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(12.dp)){Text(if(entry.third==1)"تیم اول" else "تیم دوم",color=Color(0xFFD4D9E0),style=MaterialTheme.typography.labelSmall);Text(entry.first,color=Color.White,fontWeight=FontWeight.Bold);Text(entry.second.toString(),color=Color(0xFFD4D9E0),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black);Text("انتخاب نوبت",color=Color(0xFFBFC6D0),style=MaterialTheme.typography.labelSmall)}}}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={team=if(team==1)2 else 1;next()},modifier=Modifier.weight(1f)){Text("تعویض نوبت تیم")};TextButton(onClick={score1=0;score2=0;round=1;seconds=60;running=false;showWord=false;word="برای شروع، کلمه را دریافت کن"}){Text("شروع مجدد",color=Color(0xFFFFA7A0))}}}}}
   item { QuickMathGame() }
   item { ColorMemoryGame() }
  }
 }
}

@Composable private fun QuickMathGame(){
 var a by remember{mutableIntStateOf((3..12).random())}
 var b by remember{mutableIntStateOf((3..12).random())}
 var answer by remember{mutableStateOf("")}
 var points by remember{mutableIntStateOf(0)}
 var message by remember{mutableStateOf("جواب درست بده و رکوردت را بالا ببر!")}
 Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF20252D)),shape=RoundedCornerShape(22.dp)){
  Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text("چالش محاسبه سریع",color=Color(0xFFD4D9E0),fontWeight=FontWeight.Bold)
   Text(a.toString()+" × "+b.toString()+" = ؟",color=Color.White,style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Black)
   Text("امتیاز: "+points,color=Color(0xFFD4D9E0),fontWeight=FontWeight.Bold)
   OutlinedTextField(answer,{answer=it.filter(Char::isDigit)},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("پاسخ")})
   Button(onClick={if(answer.toIntOrNull()==a*b){points++;message="درست بود! عالیه."}else message="جواب درست "+(a*b)+" بود.";a=(3..12).random();b=(3..12).random();answer=""},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFBFC6D0))){Text("بررسی و سؤال بعدی")}
   Text(message,color=Color(0xFFBFC6D0),style=MaterialTheme.typography.bodySmall)
  }
 }
}
@Composable private fun ColorMemoryGame(){
 val colors=remember{listOf(Color(0xFFE66B62),Color(0xFFBFC6D0),Color(0xFFD4D9E0),Color(0xFFBFC6D0))}
 val names=remember{listOf("قرمز","آبی","سبز","زرد")}
 var sequence by remember{mutableStateOf(listOf<Int>())}
 var chosen by remember{mutableStateOf(listOf<Int>())}
 var level by remember{mutableIntStateOf(1)}
 var message by remember{mutableStateOf("الگو را ببین و همان ترتیب را تکرار کن.")}
 Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF20252D)),shape=RoundedCornerShape(22.dp)){
  Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("حافظه رنگ‌ها",color=Color(0xFFD4D9E0),fontWeight=FontWeight.Bold)
   Text("مرحله "+level,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   Text(message,color=Color(0xFFBFC6D0),style=MaterialTheme.typography.bodySmall)
   if(sequence.isNotEmpty()) Text("الگو: "+sequence.joinToString(" ← "){names[it]},color=Color.White,fontWeight=FontWeight.SemiBold)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){colors.forEachIndexed{index,c->Box(Modifier.weight(1f).height(58.dp).background(c,RoundedCornerShape(14.dp)).clickable{if(sequence.isNotEmpty()){val next=chosen+index;chosen=next;if(next.size==sequence.size){if(next==sequence){level++;sequence=sequence+(0..3).random();message="درست بود! مرحله بعدی آماده است."}else{level=1;sequence=emptyList();message="ترتیب اشتباه بود؛ دوباره شروع کن."};chosen=emptyList()}}},contentAlignment=Alignment.Center){Text(names[index],color=Color.Black,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)}}}
   Button(onClick={sequence=List((level+1).coerceAtMost(8)){(0..3).random()};chosen=emptyList();message="حالا رنگ‌ها را به همان ترتیب لمس کن."},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFBFC6D0))){Text("ساخت الگوی مرحله بعد")}
  }
 }
}

private data class Film(val title:String,val year:Int,val genre:String,val duration:Int,val summary:String,val reason:String)
private val films=listOf(
 Film("Perfect Days",2023,"درام",124,"مردی در توکیو زیبایی‌های کوچک زندگی روزمره را کشف می‌کند.","مینیمال، آرام و انسانی."),
 Film("The Grand Budapest Hotel",2014,"کمدی، ماجراجویی",99,"ماجرایی پرجزئیات در هتلی باشکوه با شخصیت‌هایی عجیب.","برای لذت بردن از طراحی صحنه و رنگ."),
 Film("Arrival",2016,"علمی‌تخیلی، درام",116,"زبان‌شناسی برای ارتباط با موجوداتی فرازمینی فراخوانده می‌شود.","هوشمند، احساسی و فکری."),
 Film("In the Mood for Love",2000,"عاشقانه، درام",98,"دو همسایه در هنگ‌کنگ با تنهایی و احساسات ناگفته روبه‌رو می‌شوند.","تصویرسازی شاعرانه و موسیقی ماندگار."),
 Film("Whiplash",2014,"درام، موسیقی",107,"نوازنده‌ای جوان زیر فشار استادش برای کمال تلاش می‌کند.","پرکشش و پرانرژی."),
 Film("Spirited Away",2001,"انیمیشن، فانتزی",125,"دختری وارد جهانی جادویی می‌شود و باید شجاعت پیدا کند.","خیال‌انگیز با طراحی هنری فوق‌العاده."),
 Film("The Secret Life of Walter Mitty",2013,"ماجراجویی",114,"مردی خیال‌پرداز از روزمرگی بیرون می‌آید و سفر می‌کند.","برای حال‌وهوای سفر و شروع دوباره."),
 Film("Parasite",2019,"هیجان‌انگیز، درام",132,"برخورد دو خانواده از طبقات اجتماعی متفاوت به اتفاقاتی غیرمنتظره می‌رسد.","روایت چندلایه و غافلگیری."),
 Film("Her",2013,"عاشقانه، علمی‌تخیلی",126,"مردی تنها با یک سیستم‌عامل هوشمند رابطه‌ای عاطفی برقرار می‌کند.","برای فکر کردن به تنهایی و فناوری."),
 Film("Cinema Paradiso",1988,"درام",155,"خاطرات کودکی و دوستی پسری با آپاراتچی سینما در ایتالیا.","نوستالژیک و گرم؛ نامه‌ای عاشقانه به سینما.")
)
private data class Track(val title:String,val artist:String,val album:String,val preview:String)
private class TrackSearch{
 private val http=OkHttpClient()
 suspend fun search(q:String):List<Track> = withContext(Dispatchers.IO){
  val url="https://itunes.apple.com/search?term="+URLEncoder.encode(q,"UTF-8")+"&entity=song&limit=25"
  http.newCall(Request.Builder().url(url).build()).execute().use{r->if(!r.isSuccessful)throw IllegalStateException("جست‌وجوی موسیقی ناموفق بود");val a=JSONObject(r.body?.string().orEmpty()).optJSONArray("results")?:org.json.JSONArray();(0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;val p=o.optString("previewUrl");if(p.isBlank()||p=="null")null else Track(o.optString("trackName"),o.optString("artistName"),o.optString("collectionName"),p)}}
 }
}
@Composable private fun FilmPoster(title:String){
 var bitmap by remember(title){mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)}
 var failed by remember(title){mutableStateOf(false)}
 LaunchedEffect(title){
  bitmap=null
  failed=false
  try{
   val http=OkHttpClient.Builder()
    .connectTimeout(12,java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(18,java.util.concurrent.TimeUnit.SECONDS)
    .build()
   val artworkUrl=withContext(Dispatchers.IO){
    val q=URLEncoder.encode(title,"UTF-8")
    val api="https://itunes.apple.com/search?term="+q+"&media=movie&entity=movie&limit=15&country=us"
    http.newCall(Request.Builder().url(api).header("User-Agent","NaeemAI/2.0 Android").build()).execute().use{response->
     if(!response.isSuccessful) throw IllegalStateException("جست‌وجوی پوستر ناموفق بود: "+response.code)
     val results=JSONObject(response.body?.string().orEmpty()).optJSONArray("results")?:org.json.JSONArray()
     val candidates=(0 until results.length()).mapNotNull{index->results.optJSONObject(index)}
     val match=candidates.firstOrNull{item->
      val name=item.optString("trackName").ifBlank{item.optString("collectionName")}
      name.equals(title,true)||name.contains(title,true)||title.contains(name,true)
     }?:candidates.firstOrNull()
     match?.let{item->
      item.optString("artworkUrl600").takeIf{it.startsWith("https://")}
       ?:item.optString("artworkUrl512").takeIf{it.startsWith("https://")}
       ?:item.optString("artworkUrl100").takeIf{it.startsWith("https://")}
     }.orEmpty()
    }
   }
   if(artworkUrl.isNotBlank()){
    val imageBytes=withContext(Dispatchers.IO){
     http.newCall(Request.Builder().url(artworkUrl.replace("100x100","600x900").replace("100x100bb","600x900bb")).header("User-Agent","NaeemAI/2.0 Android").build()).execute().use{response->
      if(!response.isSuccessful) throw IllegalStateException("دریافت تصویر ناموفق بود")
      response.body?.bytes()
     }
    }
    if(imageBytes!=null){
     val decoded=withContext(Dispatchers.Default){BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.size)?.asImageBitmap()}
     if(decoded!=null) bitmap=decoded else failed=true
    }else failed=true
   }else failed=true
  }catch(_:Exception){failed=true}
 }
 Box(Modifier.width(112.dp).height(164.dp).background(Color(0xFF303741),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){
  if(bitmap!=null) Image(bitmap!!,contentDescription="پوستر فیلم $title",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  else Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
   Icon(Icons.Default.Movie,null,tint=Color(0xFFD4D9E0),modifier=Modifier.size(32.dp))
   Text(if(failed)"پوستر موجود نیست" else "در حال دریافت…",color=Color(0xFFA8ADB8),style=MaterialTheme.typography.labelSmall)
  }
 }
}
@Composable fun DiscoverDashboard(onClose:()->Unit,onAskAi:(String)->Unit,initialTab:String="فیلم امروز",standalone:Boolean=false){
 val ctx=LocalContext.current;var tab by remember{mutableStateOf(initialTab)};var query by remember{mutableStateOf("")};var mood by remember{mutableStateOf("همه")};var tracks by remember{mutableStateOf<List<Track>>(emptyList())};var loading by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var playing by remember{mutableStateOf<String?>(null)};var player by remember{mutableStateOf<MediaPlayer?>(null)}
 val scope=rememberCoroutineScope();val search=remember{TrackSearch()};val today=films[((System.currentTimeMillis()/86400000L)%films.size).toInt()]
 DisposableEffect(Unit){onDispose{player?.runCatching{stop()};player?.runCatching{release()}}}
 Column(Modifier.fillMaxSize().background(FB).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(standalone)"موسیقی" else "سینما",color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(if(standalone)"جست‌وجو کن، گوش بده و هنرمندهای تازه پیدا کن" else "پوسترها، فیلم‌ها و پیشنهاد تماشا",color=FM)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=FG)}}
  if(!standalone) Row{listOf("فیلم امروز","جست‌وجوی فیلم").forEach{FilterChip(selected=tab==it,onClick={tab=it},label={Text(it)})}}
  if(tab=="فیلم امروز"){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
    item{FCard{Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){FilmPoster(today.title);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("انتخاب امروز",color=FG,fontWeight=FontWeight.Bold);Text(today.title,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(today.year.toString()+" • "+today.genre+" • "+today.duration+" دقیقه",color=FG,style=MaterialTheme.typography.labelSmall);Text(today.summary,color=Color.White,style=MaterialTheme.typography.bodySmall);Text(today.reason,color=FM,style=MaterialTheme.typography.bodySmall);Button(onClick={onAskAi("درباره فیلم "+today.title+" معرفی کامل بدون اسپویل و تحلیل سبک کارگردانی بنویس.")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("تحلیل بیشتر")}}}}}
    item{Text("بر اساس حال‌وهوا",color=FG,fontWeight=FontWeight.Bold);Row{listOf("همه","آرام","پرکشش","علمی‌تخیلی","درام").forEach{FilterChip(selected=mood==it,onClick={mood=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}}
    items(films.filter{mood=="همه"||it.genre.contains(mood)||(mood=="آرام"&&it.duration>=90)||(mood=="پرکشش"&&it.genre.contains("ماجراجویی"))}){f->FCard{Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){FilmPoster(f.title);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(f.title,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(f.year.toString()+" • "+f.genre+" • "+f.duration+" دقیقه",color=FG,style=MaterialTheme.typography.labelSmall);Text(f.summary,color=FM,style=MaterialTheme.typography.bodySmall);TextButton(onClick={onAskAi("معرفی و نقد بدون اسپویل فیلم "+f.title+" را بنویس.")}){Text("نقد و معرفی بیشتر",color=FG)}}}}}
   }
  }else if(tab=="جست‌وجوی فیلم"){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
    item{FInput("نام فیلم یا ژانر",query,{query=it},Modifier.fillMaxWidth());Button(onClick={onAskAi("چند فیلم خوب دربارهٔ "+query+" معرفی کن و خلاصه و دلیل انتخابشان را بگو.")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("پیشنهاد با هوش مصنوعی")}}
    items(films.filter{query.isBlank()||it.title.contains(query,true)||it.genre.contains(query,true)}){f->FCard{Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){FilmPoster(f.title);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(f.title,color=Color.White,fontWeight=FontWeight.Bold);Text(f.year.toString()+" • "+f.genre,color=FG);Text(f.summary,color=FM,style=MaterialTheme.typography.bodySmall)}}}}
   }
  }else{
   Text("جست‌وجوی آهنگ و هنرمند",color=FG,fontWeight=FontWeight.Bold)
   Text("پخش داخل برنامه از پیش‌نمایش رسمی حدود ۳۰ ثانیه‌ای iTunes استفاده می‌کند.",color=FM,style=MaterialTheme.typography.bodySmall)
   Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(query,{query=it},modifier=Modifier.weight(1f),singleLine=true,placeholder={Text("نام خواننده، آهنگ یا سبک")});IconButton(onClick={if(query.isNotBlank()){loading=true;error="";scope.launch{try{tracks=search.search(query)}catch(e:Exception){error=e.message?:"خطا"}finally{loading=false}}}}){Icon(Icons.Default.Search,null,tint=FG)}}
   if(loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=FG)
   if(error.isNotBlank())Text(error,color=Color(0xFFFF9A91))
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(vertical=8.dp)){
    items(tracks){t->Card(colors=CardDefaults.cardColors(containerColor=FC)){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(t.title,color=Color.White,fontWeight=FontWeight.SemiBold);Text(t.artist+" • "+t.album,color=FM,style=MaterialTheme.typography.labelSmall)};IconButton(onClick={if(playing==t.preview){player?.pause();playing=null}else{player?.runCatching{stop()};player?.runCatching{release()};player=MediaPlayer().apply{setDataSource(t.preview);setOnPreparedListener{it.start();playing=t.preview};setOnCompletionListener{playing=null;it.release()};prepareAsync()}}}){Icon(if(playing==t.preview)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=FG)}}}}
   }
   if(query.isNotBlank())TextButton(onClick={onAskAi("سبک موسیقی "+query+" را تحلیل کن، ویژگی‌های صوتی و حال‌وهوایش را توضیح بده و هنرمندان مشابه معرفی کن.")}){Text("تحلیل سبک موسیقی با هوش مصنوعی",color=FG)}
  }
 }
}

@Composable fun MusicDashboard(onClose:()->Unit,onAskAi:(String)->Unit){ DiscoverDashboard(onClose,onAskAi,"موسیقی",true) }

// Graphite and silver 3D feature gallery for Naeem AI.


@Composable
private fun FeatureMapTree(onClose: () -> Unit) {
    val silver = Color(0xFFD4D9E0)
    val muted = Color(0xFF9AA3AE)
    val panel = Color(0xFF171B20)
    val line = Color(0xFF626C78)
    data class Branch(val title: String, val items: List<String>, val status: String = "در کد برنامه")
    val branches = listOf(
        Branch("هوش مصنوعی و گفتگو", listOf("چت هوشمند و پاسخ‌گویی", "گفت‌وگوی صوتی و خواندن پاسخ", "تحلیل تصویر و ساخت تصویر", "جست‌وجو و پژوهش وب", "تاریخچه و چند گفت‌وگو")),
        Branch("حافظه هوشمند", listOf("یادداشت‌های قابل مدیریت", "استفاده از یادداشت‌های منتخب در گفتگو")),
        Branch("مدیریت مالی", listOf("ثبت درآمد و هزینه", "دسته‌بندی تراکنش‌ها", "گزارش ماهانه و تراز مالی")),
        Branch("ورزش", listOf("ثبت تمرین و فعالیت", "مدت، مسافت و جزئیات تمرین", "برنامه و سوابق ورزشی")),
        Branch("سفر", listOf("مدیریت سفر و همراهان", "ثبت مخارج و سهم افراد", "آب‌وهوا و مسیر‌یابی آنلاین")),
        Branch("سلامت و مراقبت روزانه", listOf("پروفایل بدن و محاسبه شاخص‌ها", "ثبت غذا و کالری از طریق گفتگو", "تقویم چرخه قاعدگی و ثبت علائم", "یادآور دارو و ثبت زمان مصرف"), "اتصال گفتگو و یادآورها در حال توسعه"),
        Branch("برنامه‌ریز و بیدارباش", listOf("گفتن برنامه هفتگی در گفتگو", "تشخیص روزها و ساعت‌های بیداری", "تنظیم خودکار زنگ گوشی با اجازه کاربر", "تغییر یا لغو برنامه از طریق گفتگو"), "هنوز پیاده‌سازی نشده"),
        Branch("طبیعت‌گردی | Naeem Trail", listOf("ثبت مسیر با GPS", "ذخیره نقاط و محاسبه مسافت", "ورود و خروجی GPX", "نقشه توپوگرافی آفلاین سراسر ایران", "مسیریابی آفلاین و هشدار خروج از مسیر"), "نقشه و مسیریابی آفلاین در حال توسعه"),
        Branch("ابزارها و امنیت", listOf("قفل اثر انگشت یا قفل دستگاه", "تنظیم نشانی API و مدل هوش مصنوعی", "ذخیره امن کلید API"))
    )
    Column(Modifier.fillMaxSize().background(Color(0xFF090B0E)).padding(horizontal=18.dp, vertical=14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NAEEM AI  /  FEATURE MAP", color = muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("نقشه قابلیت‌های نرم‌افزار", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = silver) }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(color = Color(0xFF242A32), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountTree, null, tint = silver, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(5.dp))
                        Text("Naeem AI", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        Text("هسته هوشمند مرکزی و هماهنگ‌کننده ماژول‌ها", color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Box(Modifier.width(2.dp).height(18.dp).background(line))
            }
            items(branches.size) { index ->
                val branch = branches[index]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(2.dp).height(12.dp).background(line))
                    Surface(
                        color = panel,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF343B45)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(silver, CircleShape))
                                Spacer(Modifier.width(9.dp))
                                Text(branch.title, color = silver, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(branch.status, color = muted, style = MaterialTheme.typography.labelSmall)
                            }
                            branch.items.forEach { item ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("└", color = line)
                                    Text(item, color = Color(0xFFD0D5DC), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("وضعیت قابلیت‌ها بر اساس کد فعلی برنامه است؛ موارد در حال توسعه هنوز قابلیت کامل و آزموده‌شده محسوب نمی‌شوند.", color = muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


@Composable
fun SocialAnalyticsDashboard(onClose: () -> Unit, onAskAi: (String) -> Unit) {
    var platform by remember { mutableStateOf("Instagram") }
    var account by remember { mutableStateOf("") }
    var followers by remember { mutableStateOf("") }
    var reach by remember { mutableStateOf("") }
    var views by remember { mutableStateOf("") }
    var likes by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }
    var saves by remember { mutableStateOf("") }
    var shares by remember { mutableStateOf("") }
    var posts by remember { mutableStateOf("") }
    val platforms = listOf("Instagram", "TikTok", "YouTube", "Telegram", "LinkedIn", "X", "سایر")
    Column(
        Modifier.fillMaxSize().background(FB).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = FG) }
            Column(Modifier.weight(1f)) {
                Text("تحلیل شبکه‌های اجتماعی", color = FG, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("تحلیل صفحه خودت، مستقل از هر برند", color = FM, style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(color = FC, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF80CFA6))
                    Spacer(Modifier.width(8.dp))
                    Text("ورود امن و حریم خصوصی", color = FG, fontWeight = FontWeight.SemiBold)
                }
                Text("رمز عبورت را داخل Naeem AI وارد نکن. اتصال مستقیم اینستاگرام از مسیر رسمی Meta در حال آماده‌سازی است و پس از ثبت اپ و مجوزها فعال می‌شود. فعلاً می‌توانی آمار Insights را وارد کنی و تحلیل بگیری.", color = FM, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("پلتفرم", color = FG, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            platforms.take(4).forEach { p ->
                FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            platforms.drop(4).forEach { p ->
                FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p) })
            }
        }
        OutlinedTextField(value = account, onValueChange = { account = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نام کاربری یا لینک صفحه") }, singleLine = true)
        Text("آمار دوره اخیر (مثلاً ۳۰ روز گذشته)", color = FG, fontWeight = FontWeight.SemiBold)
        @Composable fun statField(label: String, value: String, onValue: (String) -> Unit) {
            OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        statField("تعداد دنبال‌کننده", followers) { followers = it }
        statField("Reach / دسترسی", reach) { reach = it }
        statField("بازدید محتوا", views) { views = it }
        statField("لایک", likes) { likes = it }
        statField("کامنت", comments) { comments = it }
        statField("ذخیره", saves) { saves = it }
        statField("اشتراک‌گذاری", shares) { shares = it }
        statField("تعداد پست و ریلز منتشرشده", posts) { posts = it }
        Button(
            onClick = {
                val prompt = """این داده‌ها را به‌عنوان تحلیل‌گر حرفه‌ای شبکه‌های اجتماعی بررسی کن.
پلتفرم: $platform
صفحه: $account
دوره: ۳۰ روز اخیر
دنبال‌کننده: $followers
Reach: $reach
بازدید: $views
لایک: $likes
کامنت: $comments
ذخیره: $saves
اشتراک‌گذاری: $shares
تعداد پست/ریلز: $posts

نرخ تعامل را فقط با داده‌های موجود و با ذکر فرمول محاسبه کن. نقاط قوت و ضعف، کیفیت عملکرد محتوا، نسبت دسترسی به دنبال‌کننده، نشانه‌های رشد یا افت و ۵ اقدام اولویت‌دار و یک برنامه محتوایی ۷ روزه ارائه بده. داده‌های خالی را حدس نزن و برای تحلیل دقیق‌تر بگو چه آمار دیگری لازم است."""
                onAskAi(prompt)
            },
            enabled = account.isNotBlank() && (followers.isNotBlank() || reach.isNotBlank() || views.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) { Icon(Icons.Default.Analytics, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("تحلیل صفحه با هوش مصنوعی") }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
    }
}
