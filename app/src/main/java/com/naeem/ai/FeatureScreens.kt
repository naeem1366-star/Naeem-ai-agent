package com.naeem.ai

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.app.DatePickerDialog
import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val FG=SupraGlass.Silver
private val FB=SupraGlass.Night
private val FC=SupraGlass.Glass
private val FM=SupraGlass.Muted


@Composable
fun HomeSpacesOrbit(onOpen: (Int) -> Unit) {
    data class SpaceItem(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val title: String,
        val subtitle: String,
        val color: Color
    )

    val items = listOf(
        SpaceItem(Icons.Default.Luggage, "سفر", "برنامه‌ریزی و همراهان", Color(0xFFC9B58F)),
        SpaceItem(Icons.Default.Terrain, "طبیعت", "مسیر و ماجراجویی", Color(0xFF91B7A0)),
        SpaceItem(Icons.Default.FitnessCenter, "ورزش", "تمرین و پیشرفت", Color(0xFFC58E78)),
        SpaceItem(Icons.Default.TrendingUp, "بازار", "رصد دارایی‌ها", Color(0xFFC5A66F)),
        SpaceItem(Icons.Default.Psychology, "اتاق فکر", "ایده و تحلیل", Color(0xFFA997C8)),
        SpaceItem(Icons.Default.Newspaper, "اخبار", "خبر و رویداد", Color(0xFF82AEB8)),
        SpaceItem(Icons.Default.AccountBalanceWallet, "مالی", "دخل‌وخرج", Color(0xFF78B5A8)),
        SpaceItem(Icons.Default.EventNote, "روزنگار", "یادداشت روزانه", Color(0xFFC18FA4)),
        SpaceItem(Icons.Default.MusicNote, "موسیقی", "کشف و شنیدن", Color(0xFF929FC7)),
        SpaceItem(Icons.Default.Settings, "تنظیمات", "کنترل برنامه", Color(0xFFB6B0A4))
    )

    Column(
        Modifier.fillMaxWidth().height(194.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("فضاهای اختصاصی", color = SupraGlass.Silver, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("انتخاب سریع", color = SupraGlass.Muted, style = MaterialTheme.typography.labelSmall)
        }

        listOf(items.take(5), items.drop(5)).forEachIndexed { rowIndex, rowItems ->
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEachIndexed { itemIndex, item ->
                    val index = rowIndex * 5 + itemIndex
                    Surface(
                        onClick = { onOpen(index) },
                        modifier = Modifier.width(106.dp).height(77.dp),
                        shape = RoundedCornerShape(17.dp),
                        color = SupraGlass.Glass,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SupraGlass.Edge),
                        shadowElevation = 5.dp
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                Modifier.size(31.dp).background(item.color.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = item.title, tint = SupraGlass.Ice, modifier = Modifier.size(18.dp))
                            }
                            Text(item.title, color = SupraGlass.Silver, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Text(item.subtitle, color = SupraGlass.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun FeatureHub(
    isEnglish: Boolean = false,
    accessToken: String = "",
    gatewayUrl: String = "",
    onClose:()->Unit,onFinance:()->Unit,onHealth:()->Unit,onGames:()->Unit,
    onDiscover:()->Unit,onMusic:()->Unit,onPodcast:()->Unit,onVideo:()->Unit,onMemory:()->Unit,onAlbum:()->Unit,
    onSettings:()->Unit,onTravel:()->Unit,onSport:()->Unit,onMarkets:()->Unit,onWrapped:()->Unit,onJournal:()->Unit,onTrail:()->Unit,onSocial:()->Unit,
    onNews:()->Unit,onMedication:()->Unit,onPlanner:()->Unit,onThinkRoom:()->Unit,onFuture:()->Unit,onProfessionalVideo:()->Unit,onPcControl:()->Unit = {},onWallet:()->Unit = {},onProductLens:()->Unit = {}
){
    var query by remember { mutableStateOf("") }
    var hasPaidAccess by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("free") }
    LaunchedEffect(accessToken, gatewayUrl) {
        hasPaidAccess = false
        hasPaidAccess = false
        if (accessToken.isNotBlank() && gatewayUrl.isNotBlank()) {
            hasPaidAccess = runCatching { BillingClient(gatewayUrl).hasPaidAccess(accessToken) }.getOrDefault(false)
        }
    }
    var showFeatureMap by remember { mutableStateOf(false) }
    var orbitRotation by remember { mutableStateOf(0f) }
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
        Entry("نبض بازار","تحلیل و پیشنهاد رمزارز","قیمت‌ها، رتبه‌بندی و بررسی فرصت‌های بازار","قیمت دارایی‌ها را ببین و در بخش «پیشنهاد خرید» رمزارزها را بر اساس روند ۲۴ساعته و ۷روزه، حجم معاملات، ارزش بازار و شدت نوسان رتبه‌بندی کن. هر پیشنهاد همراه با دلیل و ریسک نمایش داده می‌شود؛ تصمیم نهایی با خودت است.","قیمت بازار و غربالگری رمزارز با داده زنده؛ بدون تضمین سود",Icons.Default.TrendingUp,onMarkets,"بازار قیمت دلار طلا نقره بیت کوین رمزارز پیشنهاد خرید تحلیل ارز دیجیتال بورس ارز"),
        Entry("اتاق فکر","هم‌فکری و مدیریت پروژه","از ایده تا اجرا","ایده‌ها را با چند شخصیت فکری بررسی کن، با گوگل جست‌وجو کن و نتیجه را به پروژه و قدم اجرایی تبدیل کن.","هم‌فکری با مدل تنظیم‌شده؛ جست‌وجوی گوگل درون‌برنامه‌ای",Icons.Default.Psychology,onThinkRoom,"اتاق فکر ایده پردازی طوفان فکری پروژه استراتژی نقد جستجو گوگل"),
        Entry("به آینده وارد شو","آزمایشگاه تجربه‌های فکری","۱۲ تجربه برای خلق، نقد و تصمیم‌گیری","از شورای مخالفان و نسخه دوم خودت تا جهان‌های موازی، پرونده‌های حل‌نشده، آشوب خلاق و اتاق فرمان؛ هر تجربه را انتخاب کن و با مدل متصل‌شده پیش ببر.","تولید تجربه با مدل تنظیم‌شده؛ حافظه پایدار و زمان‌بندی خودکار هنوز مستقل پیاده‌سازی نشده‌اند",Icons.Default.AutoAwesome,onFuture,"آینده جهان موازی شورای مخالفان نسخه دوم خودم باستان‌شناس ذهن پرونده حل‌نشده ترکیب ایده مأموریت پیام آینده کالبدشکافی نقشه دانش آشوب خلاق اتاق فرمان"),
        Entry("اخبار مهم","خبرهای مهم ایران و جهان","تیترهای مهم، فوری و اثرگذار","مغز هوشمند باید خبرهای مهم را از منابع معتبر گردآوری کند، خبرهای تکراری را کنار بگذارد، آن‌ها را بر اساس موضوع و میزان اهمیت مرتب کند و خلاصه‌ای روشن همراه با منبع و زمان انتشار ارائه دهد. می‌توانی درباره هر خبر سؤال تکمیلی بپرسی.","بخش خبری و دریافت زنده خبر: هنوز پیاده‌سازی نشده",Icons.Default.Newspaper,onNews,"اخبار مهم خبر فوری ایران جهان سیاست اقتصاد فناوری جنگ بحران رویداد"),
        Entry("یادآور دارو","زمان‌بندی مصرف دارو","ثبت دارو و ساعت مصرف","برای هر دارو ساعت مصرف مشخص کن تا اعلان روزانه روی گوشی دریافت کنی.","یادآوری محلی با اعلان گوشی",Icons.Default.Medication,onMedication,"دارو یادآور قرص داروخانه مصرف"),
        Entry("نوت‌بوک","یادداشت‌ها و برنامه‌های من","ثبت کارها و اعلان","کارها را با تاریخ و ساعت ثبت کن؛ می‌توانی فهرست را مرور و موارد انجام‌شده را حذف کنی.","ذخیره محلی و اعلان زمان‌بندی‌شده",Icons.Default.CalendarMonth,onPlanner,"نوت بوک یادداشت کار برنامه ریزی فردا وظایف یادآوری"),
        Entry("دفتر مالی","مدیریت پول","درآمد، هزینه و گزارش مالی","فقط در گفتگو بگو چه مبلغی خرج کردی یا درآمد داشتی؛ مغز هوشمند مبلغ، تاریخ و دسته را درمی‌آورد، ابهام را می‌پرسد و پس از تأییدت در دفتر مالی ثبت می‌کند.","اتصال دستیار: فعال",Icons.Default.AccountBalanceWallet,onFinance,"مالی پول هزینه درآمد"),
        Entry("روایت هفته و ماه","گزارش عملکرد و روزنگار","جمع‌بندی فعالیت‌ها و اتفاق‌ها","گزارش هفت روز اخیر یا ماه جاری را ببین؛ اطلاعات ثبت‌شده در دفتر مالی و ورزش همراه با روایت‌های دسته‌بندی‌شده روزانه نمایش داده می‌شوند. گزارش فقط بر اساس داده‌های ذخیره‌شده است.","گزارش مالی، ورزشی و روزنگار گفتگو",Icons.Default.AutoAwesome,onWrapped,"گزارش هفتگی گزارش ماهانه روزنگار جمع‌بندی عملکرد"),
        Entry("روزنگار من","روایت روزهای من","اتفاق‌ها و ثبت‌های روزانه","هر چیزی که در گفت‌وگو ثبت می‌کنی یا به یکی از فضاهای تخصصی می‌سپاری، اینجا به ترتیب زمان قابل مرور است.","دفتر مستقل روزنگار",Icons.Default.EventNote,onJournal,"روزنگار ثبت روزانه رویداد اتفاقات خاطرات"),
        Entry("سلامت و تغذیه","بدن، تغذیه و مراقبت‌های روزانه","غذا، کالری، ثبت چرخه قاعدگی و داروها","وعده غذایی و مقدار آن را در گفتگو بگو؛ دستیار اطلاعات غذا را در دفتر سلامت ثبت می‌کند و برای غذای ناشناخته کالری را بدون حدس‌زدن نامشخص نگه می‌دارد. ثبت چرخه قاعدگی از همین داشبورد انجام می‌شود.","ثبت غذا از گفتگو: فعال؛ سایر قابلیت‌های سلامت در حال توسعه",Icons.Default.Favorite,onHealth,"سلامت تغذیه کالری غذا"),
        Entry("حافظه هوشمند","یادداشت‌های من","نکته‌ها و اطلاعات مهم","وقتی در گفتگو می‌گویی «این را یادت بماند»، مغز هوشمند نکته را آماده می‌کند، برای تأیید نشان می‌دهد و پس از اجازه‌ات ذخیره می‌کند تا در گفتگوهای بعدی به کار بیاید.","اتصال دستیار: فعال",Icons.Default.Bookmark,onMemory,"حافظه یادداشت"),
        Entry("آلبوم تصاویر","آرشیو آثار","تصاویر ساخته‌شده","در گفتگو تصویر را توصیف یا اصلاح کن؛ مغز هوشمند درخواست را برای ابزار تصویر آماده می‌کند. تصاویر ساخته‌شده در آلبوم نگهداری می‌شوند و مدیریت آرشیو از همین بخش است.","ابزار تصویر متصل؛ مدیریت آرشیو در همین بخش",Icons.Default.Collections,onAlbum,"آلبوم تصویر عکس"),
        Entry("بازی‌ها","چالش و سرگرمی","پانتومیم و تجربه‌های هوشمند آینده","در بازی‌های هوشمند، مغز گفتگو می‌تواند نقش را توضیح دهد، معما بسازد و روند بازی را هدایت کند. فعلاً پانتومیم فعال است و بازی‌های داستانی دیگر هنوز در حال توسعه‌اند.","بازی‌های متصل به AI: در حال توسعه",Icons.Default.SportsEsports,onGames,"بازی سرگرمی پانتومیم"),
        Entry("سینما","تماشای فیلم و سریال رایگان","پخش درون‌برنامه‌ای و تمام‌صفحه","سرویس‌های رایگان و قانونی را بدون ساخت حساب مرور کن؛ فیلم یا سریال را انتخاب کن و در پلیر درون‌برنامه‌ای تماشا کن. دسترسی بعضی عنوان‌ها به کشور و مجوز پخش وابسته است.","سرویس‌های عمومی رایگان؛ بدون اشتراک پولی",Icons.Default.Movie,onDiscover,"فیلم سینما پخش آنلاین پلیر سریال"),
        Entry("پادکست","شنیدن و کشف برنامه‌های صوتی","پادکست‌های رایگان، RSS و پخش درون‌برنامه‌ای","پادکست‌های رایگان با فید عمومی را پیدا کن، قسمت‌ها را داخل ایما پخش کن و موارد دلخواهت را برای بعد نگه دار. امکان افزودن فید RSS عمومی هم وجود دارد.","فیدهای عمومی رایگان؛ بدون اشتراک پولی",Icons.Default.Podcasts,onPodcast,"پادکست صوتی برنامه شنیداری RSS"),
        Entry("تحلیل عکس","تحلیل تصویر و شناسایی محصول","تحلیل هوشمند تصویر با دوربین","از محصول عکس بگیر یا تصویرش را انتخاب کن؛ ایما نام، برند احتمالی و دسته‌بندی را تشخیص می‌دهد و برای پیدا کردن نمونه‌ها و فروشگاه‌ها در وب جست‌وجو می‌کند.","تشخیص تصویر با مدل بینایی متصل؛ جست‌وجوی وب",Icons.Default.ImageSearch,onProductLens,"لنز تصویری گوگل لنز شناسایی محصول عکس دوربین جستجوی کالا خرید محصول"),
        Entry("استودیو ایما","تدوین حرفه‌ای ویدیو","تایم‌لاین و خروجی MP4","چند ویدیو را وارد کن، پیش‌نمایش بگیر، ترتیب کلیپ‌ها را تغییر بده و آن‌ها را روی گوشی به یک فایل MP4 تبدیل کن. ابزارهای تدوین پیشرفته در حال توسعه‌اند.","تدوین و خروجی محلی؛ بدون API",Icons.Default.MovieCreation,onVideo,"ویدیو تدوین ادیت مونتاژ تایم لاین کپ کات فیلم سازی"),
        Entry("تولید ویدیو رایگان","ساخت ویدیو با هوش مصنوعی","تولید ویدیو از متن یا تصویر","ویدیو را از متن یا تصویر بساز. دسترسی رایگان وابسته به اعتبار و سهمیه‌ی سرویس سازنده است و ممکن است همیشه یا نامحدود در دسترس نباشد؛ از کیف پول ایما برای این بخش کسر نمی‌شود.","سرویس تولید ویدیو با سهمیه‌ی رایگان محدود",Icons.Default.AutoAwesome,onProfessionalVideo,"تولید ویدیو رایگان هوش مصنوعی عکس به ویدیو text to video image to video Kling Seedance Veo Gemini"),
        Entry("موسیقی","آهنگ و هنرمند","جست‌وجو و کشف موسیقی","حال‌وهوا، سبک یا هنرمند موردعلاقه‌ات را در گفتگو بگو؛ مغز هوشمند پیشنهاد می‌دهد و جست‌وجو و پیش‌نمایش موسیقی در همین بخش انجام می‌شود.","پیشنهاد از دستیار: از طریق گفتگو",Icons.Default.MusicNote,onMusic,"موسیقی آهنگ"),
        Entry("تحلیل اینستاگرام","رشد و عملکرد پیج","اتصال حساب و تحلیل محتوا","آمار پیج را وارد کن تا مغز هوشمند نرخ تعامل، بازدید و عملکرد محتوا را بررسی و برای رشد پیشنهاد بدهد. تحلیل خودکار حساب پس از اتصال رسمی و دریافت مجوزهای Meta ممکن می‌شود.","تحلیل دستی: آماده؛ اتصال رسمی: نیازمند تنظیم Meta",Icons.Default.Insights,onSocial,"اینستاگرام شبکه اجتماعی تحلیل پیج رشد محتوا") ,
        Entry("تنظیمات","اتصال و حریم خصوصی","مدیریت اتصال و امنیت","از اینجا مدل هوش مصنوعی، نشانی سرویس و کلید API را تنظیم می‌کنی؛ این اتصال، مسیر ارتباط مغز هوشمند با گفتگو و ابزارهای برنامه است.","مرکز تنظیمات مغز هوشمند",Icons.Default.Settings,onSettings,"تنظیمات اتصال"),
        Entry("اتصال به کامپیوتر","کنترل رایانه از گوشی","اتصال امن به برنامه دسکتاپ ایما","برنامه Desktop Bridge را روی رایانه اجرا کن و از این بخش وضعیت اتصال را ببین و فرمان‌های مجاز را از گوشی ارسال کن.","نیازمند اجرای برنامه دسکتاپ روی رایانه",Icons.Default.Computer,onPcControl,"کامپیوتر رایانه دسکتاپ کنترل از راه دور اتصال PC desktop bridge"),
        Entry("کیف پول و اشتراک","اعتبار AI و پلن حرفه‌ای","شارژ اعتبار مصرف هوش مصنوعی و مدیریت اشتراک EMA","موجودی اعتبار AI، سفارش شارژ، خرید اشتراک حرفه‌ای و پیگیری رسید پرداخت را از این بخش مدیریت کن. اعتبار AI و مدت اشتراک مستقل از هم هستند.","اتصال به کیف پول و سفارش‌های پرداخت سمت سرور",Icons.Default.AccountBalanceWallet,onWallet,"کیف پول اعتبار شارژ اشتراک پرداخت حساب AI")
    )
    val englishNames = mapOf(
        "سفر و هم‌سفرها" to "Travel & companions", "برنامه‌ریزی سفر" to "Trip planning",
        "Naeem Trail" to "Naeem Trail", "ناوبری طبیعت‌گردی" to "Outdoor navigation",
        "باشگاه ورزش" to "Sports club", "تمرین و آمادگی" to "Training & fitness",
        "نبض بازار" to "Market pulse", "تحلیل و پیشنهاد رمزارز" to "Crypto analysis & ideas",
        "اتاق فکر" to "Think room", "هم‌فکری و مدیریت پروژه" to "Ideas & project planning",
        "به آینده وارد شو" to "Future gateway", "آزمایشگاه تجربه‌های فکری" to "Future experiences lab",
        "اخبار مهم" to "Top news", "خبرهای مهم ایران و جهان" to "Iran & world headlines",
        "یادآور دارو" to "Medication reminder", "زمان‌بندی مصرف دارو" to "Medication schedule",
        "نوت‌بوک" to "Notebook", "یادداشت‌ها و برنامه‌های من" to "My notes & plans",
        "دفتر مالی" to "Finance", "مدیریت پول" to "Money management",
        "روایت هفته و ماه" to "Weekly & monthly review", "گزارش عملکرد و روزنگار" to "Activity & journal summary",
        "روزنگار من" to "My journal", "روایت روزهای من" to "My daily stories",
        "سلامت و تغذیه" to "Health & nutrition", "بدن، تغذیه و مراقبت‌های روزانه" to "Body, nutrition & daily care",
        "حافظه هوشمند" to "Smart memory", "یادداشت‌های من" to "My saved notes",
        "آلبوم تصاویر" to "Image album", "آرشیو آثار" to "Created images",
        "بازی‌ها" to "Games", "چالش و سرگرمی" to "Challenges & fun",
        "سینما" to "Cinema", "فیلم و پوستر" to "Movies & posters",
        "پادکست" to "Podcasts", "شنیدن و کشف برنامه‌های صوتی" to "Discover and listen to podcasts",
        "استودیو ایما" to "EMA Studio", "تدوین حرفه‌ای ویدیو" to "Video editing",
        "تولید ویدیو رایگان" to "Free video generation", "ساخت ویدیو با هوش مصنوعی" to "AI video generation",
        "موسیقی" to "Music", "آهنگ و هنرمند" to "Songs & artists",
        "تحلیل اینستاگرام" to "Instagram analytics", "رشد و عملکرد پیج" to "Account growth & performance",
        "تنظیمات" to "Settings", "اتصال و حریم خصوصی" to "Connections & privacy",
        "اتصال به کامپیوتر" to "Connect to computer", "کنترل رایانه از گوشی" to "Control your PC from your phone",
        "کیف پول و اشتراک" to "Wallet & subscription", "اعتبار AI و پلن حرفه‌ای" to "AI credit & Professional plan"
    )
    val englishContent = mapOf(
        "سفر و هم‌سفرها" to Triple(
            "Tell EMA your destination, dates, companions, or budget. It extracts trip details, asks about missing information, and shows a summary for your approval before saving or changing anything.",
            "AI-assisted trip planning", "Connected"),
        "Naeem Trail" to Triple(
            "Describe your route and destination in chat. Automatic route recording, analysis, and offline map support are still being completed.",
            "Trail guidance integration", "In development"),
        "باشگاه ورزش" to Triple(
            "Describe a workout by text or voice. EMA identifies the activity, duration, and distance, asks when details are unclear, and saves it to your sports history after confirmation.",
            "AI-assisted workout logging", "Connected"),
        "نبض بازار" to Triple(
            "Review asset prices and rank cryptocurrencies by 24-hour and 7-day trends, trading volume, market cap, and volatility. Each idea includes reasons and risks; the final decision is yours.",
            "Live market data and crypto screening; no profit guarantee", "Live data"),
        "اتاق فکر" to Triple(
            "Explore ideas with different thinking perspectives, search the web, and turn the results into a project and actionable steps.",
            "Configured AI model and in-app web research", "Available"),
        "به آینده وارد شو" to Triple(
            "Choose from 12 thought experiments, from a council of critics and your future self to parallel worlds, unsolved cases, creative chaos, and a command room.",
            "Experiences use the configured model; persistent memory and independent scheduling are not yet implemented", "Partially available"),
        "اخبار مهم" to Triple(
            "The planned news service gathers important stories from reliable sources, removes duplicates, ranks them by topic and impact, and presents clear summaries with sources and publication times.",
            "Live news aggregation", "Not implemented yet"),
        "یادآور دارو" to Triple(
            "Set a reminder time for each medication and receive a daily notification on your phone.",
            "Local reminders with phone notifications", "Available"),
        "نوت‌بوک" to Triple(
            "Add tasks with a date and time, review your list, and remove completed items.",
            "Local storage and scheduled notifications", "Available"),
        "دفتر مالی" to Triple(
            "Tell EMA what you spent or earned. It extracts the amount, date, and category, asks about unclear details, and records the entry after your confirmation.",
            "AI-assisted finance logging", "Connected"),
        "روایت هفته و ماه" to Triple(
            "Review the last seven days or the current month. Saved finance and workout records, along with categorized daily notes, appear in the report. Summaries use stored data only.",
            "Finance, sports, and journal summaries", "Available"),
        "روزنگار من" to Triple(
            "Review everything you save in chat or send to a dedicated space, organized in chronological order.",
            "Standalone journal", "Available"),
        "سلامت و تغذیه" to Triple(
            "Tell EMA what you ate and how much. Food entries are saved in your health log; unknown foods remain unestimated rather than receiving guessed calories. Cycle tracking is available in this dashboard.",
            "Food logging from chat; other health tools are in development", "Partially available"),
        "حافظه هوشمند" to Triple(
            "When you ask EMA to remember something, it prepares the note for your review and saves it only after your approval for use in future chats.",
            "AI-assisted memory", "Connected"),
        "آلبوم تصاویر" to Triple(
            "Describe or refine an image in chat. EMA prepares the request for the image tool, and generated images are kept in this album for browsing and management.",
            "Image tool connected; album management in this space", "Available"),
        "بازی‌ها" to Triple(
            "AI games can explain roles, create puzzles, and guide play. Charades is currently available; other story-based games are still in development.",
            "AI-connected games", "In development"),
        "سینما" to Triple(
            "Tell EMA your taste or mood. It can suggest films, introduce them without spoilers, or analyze their style and story. Search and saved items live in the Cinema space.",
            "AI recommendations and analysis", "Available"),
        "پادکست" to Triple(
            "Find free podcasts from public feeds, play episodes inside EMA, save favorites, and add public RSS feeds.",
            "Free public feeds; no paid subscription required", "Available"),
        "استودیو ایما" to Triple(
            "Import several videos, preview them, rearrange clips, and export a single MP4 on your phone. Advanced editing tools are still in development.",
            "On-device editing and export; no API required", "Basic editing available"),
        "تولید ویدیو رایگان" to Triple(
            "Create videos from text or images. Free access depends on the provider's available trial credits and quota; it may be limited and is not guaranteed to be unlimited.",
            "Free provider quota when available; no EMA wallet deduction", "Limited free quota"),
        "موسیقی" to Triple(
            "Tell EMA the mood, genre, or artist you like. It can suggest music and help search and preview tracks in this space.",
            "Recommendations through chat", "Available"),
        "تحلیل اینستاگرام" to Triple(
            "Enter account statistics to review engagement, views, and content performance and receive growth suggestions. Automatic account analysis requires official Meta authorization.",
            "Manual analysis ready; official connection requires Meta setup", "Partially available"),
        "تنظیمات" to Triple(
            "Manage AI connection settings, service address, model, and privacy options. Provider credentials should be handled securely.",
            "AI connection and privacy center", "Available"),
        "اتصال به کامپیوتر" to Triple(
            "Run EMA Desktop Bridge on your computer, check its connection here, and send permitted commands securely from your phone.",
            "Requires EMA Desktop Bridge on your computer", "Setup required"),
        "کیف پول و اشتراک" to Triple(
            "Check AI credit, create top-up orders, purchase a Professional plan, and track payment receipts. AI credit and subscription duration are independent.",
            "Server-side wallet and payment orders", "Connected")
    )
    val displayEntries = if (isEnglish) entries.map { entry ->
        val copy = englishContent[entry.title]
        entry.copy(
            title = englishNames[entry.title] ?: entry.title,
            subtitle = englishNames[entry.subtitle] ?: entry.subtitle,
            detail = copy?.first ?: entry.detail,
            aiRole = copy?.second ?: entry.aiRole,
            aiStatus = copy?.third ?: entry.aiStatus
        )
    } else entries
    val paidTitles = setOf("نبض بازار", "اتاق فکر", "به آینده وارد شو", "تحلیل اینستاگرام")
    fun isPaid(entry: Entry) = entry.title in paidTitles
    fun hubText(fa: String, en: String) = if (isEnglish) en else fa

    if (showFeatureMap) {
        FeatureMapTree(
            onClose = { showFeatureMap = false },
            modules = displayEntries.map { Triple(it.title, it.subtitle + " · " + it.detail, it.aiStatus) }
        )
        return
    }
    val categoryEntries = displayEntries.filter { if (selectedCategory == "paid") isPaid(it) else !isPaid(it) }
    val filtered=categoryEntries.filter{query.isBlank()||it.title.contains(query,true)||it.subtitle.contains(query,true)||it.keywords.contains(query,true)}
    val gold=SupraGlass.Ice
    val champagne=SupraGlass.Silver
    val hubBg=SupraGlass.Night
    selectedEntry?.let { entry ->
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (isEnglish) androidx.compose.ui.unit.LayoutDirection.Ltr else androidx.compose.ui.unit.LayoutDirection.Rtl) {
            AlertDialog(
                onDismissRequest = { selectedEntry = null },
                containerColor = Color(0xFF101B2B),
                titleContentColor = Color(0xFFE0E6EE),
                textContentColor = Color(0xFFB8C2CE),
                title = { Text(entry.title, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = if (isEnglish) Alignment.Start else Alignment.End) {
                        Text(entry.detail, style = MaterialTheme.typography.bodyMedium, textAlign = if (isEnglish) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
                        Divider(color = Color(0xFF3B4551))
                        Text(hubText("ارتباط با مغز هوشمند", "AI integration"), color = Color(0xFFE0E6EE), fontWeight = FontWeight.SemiBold, textAlign = if (isEnglish) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
                        Text(entry.aiRole, style = MaterialTheme.typography.bodySmall, textAlign = if (isEnglish) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
                        Surface(color = Color(0xFF252D37), shape = RoundedCornerShape(10.dp)) {
                            Text(entry.aiStatus, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Color(0xFFD4DCE6), style = MaterialTheme.typography.labelSmall)
                        }
                        if (isPaid(entry) && !hasPaidAccess) {
                            Text(
                                hubText("برای استفاده از این ابزار باید حساب ایما را فعال کنی یا کیف پولت را شارژ کنی. ورود مهمان فقط به ابزارهای رایگان دسترسی دارد.",
                                    "Activate an EMA plan or add wallet credit to use this tool. Guest access is limited to free tools."),
                                color = Color(0xFFFFD18A), style = MaterialTheme.typography.bodySmall,
                                textAlign = if (isEnglish) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedEntry = null
                        if (isPaid(entry) && !hasPaidAccess) onWallet() else entry.action()
                    }) {
                        Text(
                            if (isPaid(entry) && !hasPaidAccess) hubText("خرید حساب / شارژ کیف پول", "Get access / add credit")
                            else hubText("ورود به این بخش", "Open this space"),
                            color = Color(0xFFE0E6EE)
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedEntry = null }) { Text(hubText("بستن", "Close"), color = Color(0xFF9BA7B5)) }
                }
            )
        }
    }
    var centerIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, filtered.size) { centerIndex = 0 }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides (if (isEnglish) androidx.compose.ui.unit.LayoutDirection.Ltr else androidx.compose.ui.unit.LayoutDirection.Rtl)) {
    Column(Modifier.fillMaxSize().background(Color.Transparent).padding(horizontal=18.dp, vertical=14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f), horizontalAlignment=if (isEnglish) Alignment.Start else Alignment.End){
                Text("EMA ASI PLATFORM  /  SPACES",color=gold.copy(alpha=.82f),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Text(hubText("فضاهای من", "My spaces"),color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text(hubText("ابزارها و فضاهای ایما را انتخاب کن.", "Choose an EMA space or tool."),color=Color(0xFFAAB2BD),style=MaterialTheme.typography.bodySmall,textAlign=if(isEnglish) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
            }
            IconButton(onClick={showFeatureMap=true}){Icon(Icons.Default.AccountTree,null,tint=gold)}
            IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=gold)}
        }
        Surface(modifier=Modifier.fillMaxWidth(),color=SupraGlass.Glass,shape=RoundedCornerShape(18.dp),
            border=androidx.compose.foundation.BorderStroke(1.dp,SupraGlass.Edge)){
            Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp),horizontalAlignment=if(isEnglish) Alignment.Start else Alignment.End){
                    Text(hubText("مغز هوشمند ایما", "EMA intelligent core"),color=Color(0xFFE0E6EE),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall,textAlign=androidx.compose.ui.text.style.TextAlign.End)
                    Text(hubText("وقتی گفتگو یا میکروفون را فعال می‌کنی، ایما موضوع را تشخیص می‌دهد و اطلاعات را به فضای مرتبط هدایت می‌کند؛ ثبت یا تغییر اطلاعات مهم با تأیید تو انجام می‌شود.", "When you chat or use the microphone, EMA identifies the topic and routes information to the relevant space. Important changes are made only with your confirmation."),color=Color(0xFFADB7C4),style=MaterialTheme.typography.bodySmall,textAlign=if(isEnglish) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
                }
                Icon(Icons.Default.Hub,null,tint=Color(0xFFE0E6EE),modifier=Modifier.size(27.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
            textStyle=androidx.compose.ui.text.TextStyle(textAlign=if(isEnglish) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End),
            shape=RoundedCornerShape(16.dp),placeholder={Text(hubText("جست‌وجو در فضاها…", "Search spaces…"))},
            leadingIcon={Icon(Icons.Default.Search,null,tint=gold)},
            colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=gold,unfocusedBorderColor=SupraGlass.Edge,
                focusedContainerColor=Color(0x16FFFFFF),unfocusedContainerColor=Color(0x16FFFFFF),cursorColor=gold))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedCategory == "free",
                onClick = { selectedCategory = "free"; selectedEntry = null },
                label = { Text(hubText("ابزارهای رایگان", "Free tools")) },
                leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
            )
            FilterChip(
                selected = selectedCategory == "paid",
                onClick = { selectedCategory = "paid"; selectedEntry = null },
                label = { Text(hubText("ابزارهای پولی", "Paid tools")) },
                leadingIcon = { Icon(Icons.Default.Lock, null) }
            )
        }
        if (selectedCategory == "paid" && !hasPaidAccess) {
            Text(
                hubText("این ابزارها برای همه قابل مشاهده‌اند؛ برای استفاده باید حساب بخری یا کیف پولت را شارژ کنی.",
                    "These tools are visible to everyone. A plan or wallet credit is required to use them."),
                color = Color(0xFFFFD18A), style = MaterialTheme.typography.bodySmall
            )
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(if(query.isBlank()) hubText(if (selectedCategory == "free") "ابزارهای رایگان" else "ابزارهای پولی", if (selectedCategory == "free") "Free tools" else "Paid tools") else hubText("نتایج جست‌وجو", "Search results"),color=champagne,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(if(isEnglish) "${filtered.size} items" else "${filtered.size} مورد",color=Color(0xFF8E98A5),style=MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        if(filtered.isEmpty()){
            Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){Text(hubText("قابلیتی با این نام پیدا نشد.", "No matching feature was found."),color=Color(0xFFAAB2BD))}
        }else{
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered) { entry ->
                    val accent = listOf(
                        Color(0xFF6EAFF0), Color(0xFF8CC4FF), Color(0xFF4F91D2),
                        Color(0xFFB9D8FF), Color(0xFF76AEE5), Color(0xFF5E9BD8)
                    )[entries.indexOf(entry).coerceAtLeast(0) % 6]
                    Surface(
                        onClick = {
                            if (isPaid(entry) && !hasPaidAccess) selectedEntry = entry else entry.action()
                        },
                        modifier = Modifier.fillMaxWidth().height(142.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = SupraGlass.Glass,
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.48f)),
                        shadowElevation = 5.dp
                    ) {
                        Column(
                            Modifier.fillMaxSize()
                                .background(ComposeBrush.linearGradient(listOf(accent.copy(alpha = 0.24f), Color(0x228CC4FF), Color(0x0C8CC4FF))))
                                .padding(13.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(48.dp).background(accent.copy(alpha = 0.24f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(entry.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(entry.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                                    if (isPaid(entry)) Icon(Icons.Default.Lock, null, tint = Color(0xFFFFD18A), modifier = Modifier.size(13.dp))
                                }
                                Text(entry.subtitle, color = Color(0xFFBAC5D3), maxLines = 2, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            Text(hubText("ورود به بخش", "Open space"), color = gold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={selectedEntry=filtered[centerIndex.coerceIn(0,filtered.lastIndex)]}){
                    Icon(Icons.Default.Info,null,tint=gold,modifier=Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(hubText("جزئیات فضای انتخاب‌شده", "Selected space details"),color=gold)
                }
            }
            OutlinedButton(onClick = { showFeatureMap = true }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SupraGlass.Edge),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = gold)) {
                Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(hubText("همه ابزارها", "All tools"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
    }
}
@Composable private fun FCard(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=FC),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
@Composable private fun FInput(label:String,value:String,change:(String)->Unit,modifier:Modifier=Modifier){OutlinedTextField(value,change,modifier=modifier,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=FG,unfocusedBorderColor=Color(0xFF454954)))}

internal data class HLog(val name:String,val kcal:Int,val type:String,val at:Long,val protein:Double=0.0,val carbs:Double=0.0,val fat:Double=0.0)
internal data class FoodInfo(val name:String,val kcal:Double,val protein:Double,val carbs:Double,val fat:Double)
internal val foodBank=listOf(
 FoodInfo("سینه مرغ پخته",165.0,31.0,0.0,3.6),FoodInfo("تخم مرغ",155.0,13.0,1.1,11.0),
 FoodInfo("برنج سفید پخته",130.0,2.7,28.2,0.3),FoodInfo("نان سنگک",259.0,9.0,52.0,1.5),
 FoodInfo("ماست کم‌چرب",63.0,5.3,7.0,1.6),FoodInfo("موز",89.0,1.1,22.8,0.3),
 FoodInfo("سیب",52.0,0.3,13.8,0.2),FoodInfo("بادام",579.0,21.2,21.6,49.9),
 FoodInfo("عدس پخته",116.0,9.0,20.1,0.4),FoodInfo("تن ماهی آبکش‌شده",132.0,29.0,0.0,1.0),
 FoodInfo("شیر کم‌چرب",42.0,3.4,5.0,1.0),FoodInfo("سیب‌زمینی آب‌پز",87.0,1.9,20.1,0.1)
)
internal class HStore(c:Context){
 private val p=c.getSharedPreferences("naeem_health_local",Context.MODE_PRIVATE)
 fun get(k:String,d:String="")=p.getString(k,d)?:d
 fun put(k:String,v:String){p.edit().putString(k,v).apply()}
 fun logs():List<HLog> = runCatching{val a=org.json.JSONArray(p.getString("logs","[]"));(0 until a.length()).mapNotNull{i->a.optJSONObject(i)?.let{HLog(it.optString("n"),it.optInt("c"),it.optString("t"),it.optLong("a"),it.optDouble("p"),it.optDouble("b"),it.optDouble("f"))}}}.getOrDefault(emptyList())
 fun add(x:HLog){val a=org.json.JSONArray();(logs()+x).takeLast(500).forEach{a.put(org.json.JSONObject().put("n",it.name).put("c",it.kcal).put("t",it.type).put("a",it.at).put("p",it.protein).put("b",it.carbs).put("f",it.fat))};p.edit().putString("logs",a.toString()).apply()}
 fun remove(at:Long){val a=org.json.JSONArray();logs().filterNot{it.at==at}.forEach{a.put(org.json.JSONObject().put("n",it.name).put("c",it.kcal).put("t",it.type).put("a",it.at))};p.edit().putString("logs",a.toString()).apply()}
}

private val HBg=Color.Transparent
private val HPanel=SupraGlass.Glass
private val HMint=SupraGlass.Ice
private val HMuted=Color(0xFF9AA3AE)
@Composable private fun HCard(content:@Composable ColumnScope.()->Unit){
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=HPanel),shape=RoundedCornerShape(22.dp),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp),content=content)
 }
}
@Composable private fun HInput(label:String,value:String,change:(String)->Unit,modifier:Modifier=Modifier){
 OutlinedTextField(value,change,modifier=modifier,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),shape=RoundedCornerShape(15.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=HMint,unfocusedBorderColor=SupraGlass.Edge,focusedContainerColor=Color(0x16FFFFFF),unfocusedContainerColor=Color(0x16FFFFFF),focusedLabelColor=HMint,cursorColor=HMint))
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


@Composable
private fun PeriodTrackerCard(context: Context) {
    val prefs = remember { context.getSharedPreferences("naeem_cycle_local", Context.MODE_PRIVATE) }
    var lastStart by remember { mutableLongStateOf(prefs.getLong("last_start", 0L)) }
    var cycleLength by remember { mutableStateOf(prefs.getInt("cycle_length", 28).toString()) }
    var bleedDays by remember { mutableStateOf(prefs.getInt("bleed_days", 5).toString()) }
    val symptomOptions = listOf("دل‌درد", "کمردرد", "سردرد", "خستگی", "نفخ", "تغییر خلق", "حساسیت سینه", "تهوع")
    val symptoms = remember {
        mutableStateListOf<String>().apply {
            val saved = prefs.getString("symptoms", "") ?: ""
            if (saved.isNotBlank()) addAll(saved.split("|").filter { it in symptomOptions })
        }
    }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }
    val cycle = cycleLength.toIntOrNull()?.coerceIn(15, 60) ?: 28
    val bleed = bleedDays.toIntOrNull()?.coerceIn(1, 15) ?: 5
    val nextStart = if (lastStart > 0L) Calendar.getInstance().apply {
        timeInMillis = lastStart
        add(Calendar.DAY_OF_YEAR, cycle)
    }.timeInMillis else 0L
    val periodEnd = if (lastStart > 0L) Calendar.getInstance().apply {
        timeInMillis = lastStart
        add(Calendar.DAY_OF_YEAR, bleed - 1)
    }.timeInMillis else 0L

    HCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth, null, tint = HMint)
            Spacer(Modifier.width(8.dp))
            Text("تقویم چرخه قاعدگی", color = HMint, fontWeight = FontWeight.Bold)
        }
        Text("اطلاعات چرخه فقط روی همین گوشی ذخیره می‌شود.", color = HMuted, style = MaterialTheme.typography.bodySmall)
        Text("تاریخ شروع آخرین پریود", color = Color.White, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = {
                val initial = Calendar.getInstance().apply {
                    if (lastStart > 0L) timeInMillis = lastStart
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        lastStart = Calendar.getInstance().apply {
                            set(year, month, day, 12, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    },
                    initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CalendarMonth, null)
            Spacer(Modifier.width(8.dp))
            Text(if (lastStart > 0L) dateFormat.format(Date(lastStart)) else "انتخاب تاریخ شروع")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HInput("طول چرخه (روز)", cycleLength, { cycleLength = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f))
            HInput("مدت خون‌ریزی (روز)", bleedDays, { bleedDays = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f))
        }
        Text("علائم تجربه‌شده", color = Color.White, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            symptomOptions.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { symptom ->
                        FilterChip(
                            selected = symptom in symptoms,
                            onClick = {
                                if (symptom in symptoms) symptoms.remove(symptom) else symptoms.add(symptom)
                            },
                            label = { Text(symptom, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
        Button(
            onClick = {
                prefs.edit()
                    .putLong("last_start", lastStart)
                    .putInt("cycle_length", cycle)
                    .putInt("bleed_days", bleed)
                    .putString("symptoms", symptoms.joinToString("|"))
                    .apply()
            },
            enabled = lastStart > 0L && (cycleLength.toIntOrNull() ?: 0) in 15..60 && (bleedDays.toIntOrNull() ?: 0) in 1..15,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = HMint, contentColor = FB)
        ) {
            Icon(Icons.Default.Save, null)
            Spacer(Modifier.width(7.dp))
            Text("ذخیره اطلاعات چرخه")
        }
        if (lastStart > 0L) {
            Divider(color = FB)
            Text("برآورد چرخه بعدی", color = HMint, fontWeight = FontWeight.Bold)
            Text("شروع احتمالی دوره بعد: " + dateFormat.format(Date(nextStart)), color = Color.White)
            Text("پایان احتمالی خون‌ریزی ثبت‌شده: " + dateFormat.format(Date(periodEnd)), color = HMuted, style = MaterialTheme.typography.bodySmall)
            Text("طول چرخه تنظیم‌شده: $cycle روز", color = HMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("برای دیدن برآورد دوره بعد، تاریخ شروع آخرین پریود را انتخاب و اطلاعات را ذخیره کن.", color = HMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "این تاریخ‌ها تقریبی‌اند؛ چرخه می‌تواند تغییر کند. این ابزار تشخیص پزشکی نیست و نباید برای پیشگیری از بارداری یا تعیین قطعی زمان باروری استفاده شود.",
            color = Color(0xFFFFD39A),
            style = MaterialTheme.typography.labelSmall
        )
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
 Column(Modifier.fillMaxSize().background(HBg.copy(alpha=0.84f)).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   Box(Modifier.size(48.dp).background(SupraGlass.Glass,RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Eco,null,tint=HMint,modifier=Modifier.size(29.dp))}
   Spacer(Modifier.width(11.dp))
   Column(Modifier.weight(1f)){Text("سلامت و تغذیه",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("داشبورد بدن و انرژی",color=HMuted,style=MaterialTheme.typography.bodySmall)}
   IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=HMint)}
  }
  Card(colors=CardDefaults.cardColors(containerColor=HPanel),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
   Box(Modifier.fillMaxWidth().background(ComposeBrush.linearGradient(listOf(Color(0x223F8DDA),Color(0x122B5C91),Color(0x1A17395B)))).padding(17.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
      Text("HEALTH SPACE",color=HMint,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
      Text("قدم‌به‌قدم، سالم‌تر",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)
      Text("غذا، فعالیت، چرخه قاعدگی و داروها را در یک پرونده دنبال کن؛ برنامه هفتگی و بیدارباش هوشمند هم در مسیر توسعه است.",color=SupraGlass.Ice,style=MaterialTheme.typography.bodySmall)
     }
     Box(Modifier.size(68.dp).background(ComposeBrush.radialGradient(listOf(SupraGlass.Ice,Color(0xFF4F91D2),Color.Transparent)),CircleShape),contentAlignment=Alignment.Center){
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
   item { PeriodTrackerCard(ctx) }
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
    var selected by remember { mutableStateOf("") }
    val games = listOf(
        Triple("پانتومیم", "بازی گروهی و زمان‌دار", SupraGlass.Ice),
        Triple("محاسبه سریع", "سرعت و دقت ذهن", Color(0xFF91BFE8)),
        Triple("حافظه رنگ‌ها", "تمرکز و حافظه دیداری", SupraGlass.Ice),
        Triple("مافیا", "نقش مخفی و استدلال گروهی", SupraGlass.Ice)
    )
    if (selected.isNotEmpty()) {
        when (selected) {
            "پانتومیم" -> CharadesGameScreen(onClose = { selected = "" })
            "محاسبه سریع" -> GameDetailFrame("محاسبه سریع", "ذهن را به چالش بکش", Color(0xFF91BFE8), { selected = "" }) { QuickMathGame() }
            "حافظه رنگ‌ها" -> GameDetailFrame("حافظه رنگ‌ها", "الگو را به خاطر بسپار", SupraGlass.Ice, { selected = "" }) { ColorMemoryGame() }
            "مافیا" -> GameDetailFrame("مافیا", "شب، روز و نقش‌های پنهان", SupraGlass.Ice, { selected = "" }) { MafiaGame() }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(
        ComposeBrush.verticalGradient(listOf(SupraGlass.Night, SupraGlass.Deep, Color(0xFF102744)))
    ).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("EMA ASI PLATFORM  /  GAME STUDIO", color = SupraGlass.Ice, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("باشگاه بازی", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("هر بازی، یک تجربه مستقل", color = SupraGlass.Ice, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
        }
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SupraGlass.Glass), shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).background(
                    ComposeBrush.linearGradient(listOf(SupraGlass.Ice, SupraGlass.Ice)),
                    RoundedCornerShape(18.dp)
                ), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SportsEsports, null, tint = Color(0xFF11131A), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("وقتشه بازی شروع بشه", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("یک بازی را انتخاب کن و وارد صفحه اختصاصی آن شو.", color = SupraGlass.Ice, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("بازی‌های در دسترس", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(games.size) { index ->
                val game = games[index]
                val icon = when (game.first) {
                    "پانتومیم" -> Icons.Default.TheaterComedy
                    "محاسبه سریع" -> Icons.Default.Calculate
                    "حافظه رنگ‌ها" -> Icons.Default.Palette
                    else -> Icons.Default.Visibility
                }
                Card(
                    modifier = Modifier.fillMaxWidth().height(190.dp).clickable { selected = game.first },
                    shape = RoundedCornerShape(23.dp),
                    colors = CardDefaults.cardColors(containerColor = SupraGlass.Glass),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                ) {
                    Box(Modifier.fillMaxSize().background(
                        ComposeBrush.linearGradient(listOf(game.third.copy(alpha = 0.25f), SupraGlass.Glass, Color(0xFF10131A)))
                    ).padding(15.dp)) {
                        Box(Modifier.align(Alignment.TopEnd).size(76.dp).background(game.third.copy(alpha = 0.10f), CircleShape))
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.size(54.dp).background(game.third.copy(alpha = 0.18f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = game.third, modifier = Modifier.size(31.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(game.first, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                                Text(game.second, color = SupraGlass.Ice, style = MaterialTheme.typography.labelSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("ورود به بازی", color = game.third, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(5.dp))
                                    Icon(Icons.Default.ArrowBack, null, tint = game.third, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun GameDetailFrame(
    title: String, subtitle: String, accent: Color, onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().background(
        ComposeBrush.verticalGradient(listOf(SupraGlass.Night, SupraGlass.Deep))
    ).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = accent) }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(subtitle, color = accent, style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.size(42.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SportsEsports, null, tint = accent)
            }
        }
        Spacer(Modifier.height(16.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { content() }
        }
    }
}

@Composable private fun CharadesGameScreen(onClose:()->Unit){
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
 Column(Modifier.fillMaxSize().background(Color.Transparent)){
  Row(Modifier.fillMaxWidth().background(SupraGlass.Glass).padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("GAME STUDIO",color=SupraGlass.Ice,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Text("پانتومیم تیمی",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("حدس بزن، زمان را مدیریت کن و امتیاز بگیر",color=SupraGlass.Ice,style=MaterialTheme.typography.bodySmall)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=Color.White)}}
  LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   item{Card(colors=CardDefaults.cardColors(containerColor=SupraGlass.Glass),shape=RoundedCornerShape(26.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("دور " + round,color=Color(0xFFD4D9E0),fontWeight=FontWeight.Bold);Text("پانتومیم تیمی",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)};Surface(color=SupraGlass.Glass,shape=CircleShape){Text("⏱",modifier=Modifier.padding(12.dp),color=Color.White)}}
    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("ترکیبی","سینما","سخت").forEach{FilterChip(selected=category==it,onClick={category=it},label={Text(it)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=SupraGlass.Ice,selectedLabelColor=Color.White,containerColor=SupraGlass.Glass,labelColor=Color.White))}}
    Box(Modifier.fillMaxWidth().background(Color.Transparent,RoundedCornerShape(22.dp)).padding(vertical=25.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Text("کلمهٔ مخصوص اجرا",color=Color(0xFFD4D9E0));Text(if(showWord)word else "برای نمایش کلمه بزن",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text(String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60),color=if(seconds<=10)Color(0xFFFF817D) else Color(0xFFD4D9E0),style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Black);Text(if(seconds==0)"زمان تمام شد!" else "زمان باقی‌مانده",color=SupraGlass.Ice,style=MaterialTheme.typography.labelSmall)}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={if(!showWord){word=decks[category].orEmpty().random();showWord=true};running=!running},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=SupraGlass.Ice),shape=RoundedCornerShape(14.dp)){Icon(if(running)Icons.Default.Pause else Icons.Default.PlayArrow,null);Text(if(running)"مکث" else "شروع زمان‌سنج")};OutlinedButton(onClick={next()},modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.SkipNext,null);Text("کلمه بعدی")}}
    Button(onClick={if(team==1)score1++ else score2++;next()},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFD4D9E0),contentColor=Color(0x16FFFFFF)),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.CheckCircle,null);Text("حدس درست بود! +۱")}
   }}}
   item{Card(colors=CardDefaults.cardColors(containerColor=SupraGlass.Glass),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("جدول امتیاز",color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){listOf(Triple(team1,score1,1),Triple(team2,score2,2)).forEach{entry->Card(Modifier.weight(1f).clickable{team=entry.third},colors=CardDefaults.cardColors(containerColor=if(team==entry.third)SupraGlass.Glass else SupraGlass.Glass),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(12.dp)){Text(if(entry.third==1)"تیم اول" else "تیم دوم",color=Color(0xFFD4D9E0),style=MaterialTheme.typography.labelSmall);Text(entry.first,color=Color.White,fontWeight=FontWeight.Bold);Text(entry.second.toString(),color=Color(0xFFD4D9E0),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black);Text("انتخاب نوبت",color=SupraGlass.Ice,style=MaterialTheme.typography.labelSmall)}}}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={team=if(team==1)2 else 1;next()},modifier=Modifier.weight(1f)){Text("تعویض نوبت تیم")};TextButton(onClick={score1=0;score2=0;round=1;seconds=60;running=false;showWord=false;word="برای شروع، کلمه را دریافت کن"}){Text("شروع مجدد",color=Color(0xFFFFA7A0))}}}}}
  }
 }
}

@Composable private fun QuickMathGame(){
 var a by remember{mutableIntStateOf((3..12).random())}
 var b by remember{mutableIntStateOf((3..12).random())}
 var answer by remember{mutableStateOf("")}
 var points by remember{mutableIntStateOf(0)}
 var message by remember{mutableStateOf("جواب درست بده و رکوردت را بالا ببر!")}
 Card(colors=CardDefaults.cardColors(containerColor=SupraGlass.Glass),shape=RoundedCornerShape(22.dp)){
  Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text("چالش محاسبه سریع",color=Color(0xFFD4D9E0),fontWeight=FontWeight.Bold)
   Text(a.toString()+" × "+b.toString()+" = ؟",color=Color.White,style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Black)
   Text("امتیاز: "+points,color=Color(0xFFD4D9E0),fontWeight=FontWeight.Bold)
   OutlinedTextField(answer,{answer=it.filter(Char::isDigit)},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("پاسخ")})
   Button(onClick={if(answer.toIntOrNull()==a*b){points++;message="درست بود! عالیه."}else message="جواب درست "+(a*b)+" بود.";a=(3..12).random();b=(3..12).random();answer=""},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=SupraGlass.Ice)){Text("بررسی و سؤال بعدی")}
   Text(message,color=SupraGlass.Ice,style=MaterialTheme.typography.bodySmall)
  }
 }
}
@Composable private fun ColorMemoryGame(){
 val colors=remember{listOf(Color(0xFFE66B62),SupraGlass.Ice,Color(0xFFD4D9E0),SupraGlass.Ice)}
 val names=remember{listOf("قرمز","آبی","سبز","زرد")}
 var sequence by remember{mutableStateOf(listOf<Int>())}
 var chosen by remember{mutableStateOf(listOf<Int>())}
 var level by remember{mutableIntStateOf(1)}
 var message by remember{mutableStateOf("الگو را ببین و همان ترتیب را تکرار کن.")}
 Card(colors=CardDefaults.cardColors(containerColor=SupraGlass.Glass),shape=RoundedCornerShape(22.dp)){
  Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("حافظه رنگ‌ها",color=Color(0xFFD4D9E0),fontWeight=FontWeight.Bold)
   Text("مرحله "+level,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   Text(message,color=SupraGlass.Ice,style=MaterialTheme.typography.bodySmall)
   if(sequence.isNotEmpty()) Text("الگو: "+sequence.joinToString(" ← "){names[it]},color=Color.White,fontWeight=FontWeight.SemiBold)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){colors.forEachIndexed{index,c->Box(Modifier.weight(1f).height(58.dp).background(c,RoundedCornerShape(14.dp)).clickable{if(sequence.isNotEmpty()){val next=chosen+index;chosen=next;if(next.size==sequence.size){if(next==sequence){level++;sequence=sequence+(0..3).random();message="درست بود! مرحله بعدی آماده است."}else{level=1;sequence=emptyList();message="ترتیب اشتباه بود؛ دوباره شروع کن."};chosen=emptyList()}}},contentAlignment=Alignment.Center){Text(names[index],color=Color.Black,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)}}}
   Button(onClick={sequence=List((level+1).coerceAtMost(8)){(0..3).random()};chosen=emptyList();message="حالا رنگ‌ها را به همان ترتیب لمس کن."},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=SupraGlass.Ice)){Text("ساخت الگوی مرحله بعد")}
  }
 }
}


@Composable private fun MafiaGame(){
 var namesText by remember{mutableStateOf("آرمان، نیما، سارا، علی، مهسا، رضا")}
 var names by remember{mutableStateOf(listOf<String>())}
 var roles by remember{mutableStateOf(emptyMap<String,String>())}
 var alive by remember{mutableStateOf(listOf<String>())}
 var phase by remember{mutableStateOf("setup")}
 var index by remember{mutableIntStateOf(0)}
 var target by remember{mutableStateOf("")}
 var round by remember{mutableIntStateOf(1)}
 var note by remember{mutableStateOf("نام بازیکن‌ها را با ویرگول جدا کن.")}
 val mafia=roles.entries.firstOrNull{it.value=="مافیا"}?.key
 fun resolve(remaining:List<String>){
  val count=remaining.count{roles[it]=="مافیا"}
  if(count==0){phase="end";note="شهروندها برنده شدند؛ مافیا حذف شد."}
  else if(count>=remaining.size-count){phase="end";note="مافیا برنده شد؛ تعدادش به شهروندها رسید."}
 }
 Card(colors=CardDefaults.cardColors(containerColor=SupraGlass.Glass),shape=RoundedCornerShape(24.dp)){
  Column(Modifier.fillMaxWidth().padding(17.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){
    Box(Modifier.size(44.dp).background(ComposeBrush.linearGradient(listOf(Color(0xFF4F91D2),Color(0xFF152E4A))),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Text("♟",color=Color.White,style=MaterialTheme.typography.headlineMedium)}
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)){Text("مافیا",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text("بازی گروهی با نقش مخفی",color=SupraGlass.Ice,style=MaterialTheme.typography.labelSmall)}
    Text(if(phase=="setup")"شروع بازی" else "دور $round",color=SupraGlass.Ice,style=MaterialTheme.typography.labelSmall)
   }
   Text(note,color=SupraGlass.Muted,style=MaterialTheme.typography.bodySmall)
   when(phase){
    "setup"->{
     OutlinedTextField(namesText,{namesText=it},modifier=Modifier.fillMaxWidth(),minLines=2,label={Text("نام بازیکن‌ها")},supportingText={Text("حداقل ۵ نفر؛ نام‌ها را با ویرگول جدا کن.")})
     Button(onClick={
      val list=namesText.split(",", "،", "\n").map{it.trim()}.filter{it.isNotBlank()}.distinct()
      if(list.size<5)note="برای شروع حداقل ۵ بازیکن لازم است."
      else{names=list;alive=list;roles=list.shuffled().mapIndexed{i,n->n to if(i==0)"مافیا" else "شهروند"}.toMap();index=0;phase="deal";note="نقش‌ها ساخته شد. هر نفر جداگانه نقش خودش را ببیند."}
     },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=SupraGlass.Ice)){Text("ساخت بازی و تقسیم نقش‌ها")}
    }
    "deal"->{
     Text("گوشی دست "+names[index]+" است",color=Color.White,fontWeight=FontWeight.Bold)
     var shown by remember(index){mutableStateOf(false)}
     Box(Modifier.fillMaxWidth().height(88.dp).background(Color(0x223D86C8),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){Text(if(shown)roles[names[index]].orEmpty() else "نقش مخفی",color=SupraGlass.Silver,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black)}
     Button(onClick={shown=!shown},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF5D9DD8))){Text(if(shown)"پنهان کردن نقش" else "نمایش نقش من")}
     Button(onClick={if(index<names.lastIndex)index++ else{phase="night";round=1;note="شب شد؛ گرداننده هدف مافیا را وارد کند."}},enabled=shown,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=SupraGlass.Ice)){Text(if(index<names.lastIndex)"تحویل گوشی به نفر بعد" else "پایان تقسیم نقش‌ها")}
    }
    "night"->{
     Text("شب؛ هدف مافیا",color=Color.White,fontWeight=FontWeight.Bold)
     OutlinedTextField(target,{target=it},modifier=Modifier.fillMaxWidth(),label={Text("نام بازیکن هدف")},singleLine=true)
     Button(onClick={if(target in alive&&target!=mafia){val rest=alive-target;alive=rest;note=target+" در شب حذف شد.";target="";resolve(rest);if(phase!="end")phase="day"}else note="نام یک بازیکن زنده و غیرمافیا را دقیق وارد کن."},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=SupraGlass.Ice)){Text("اعلام صبح")}
    }
    "day"->{
     Text("روز؛ گفت‌وگو و رأی‌گیری",color=Color.White,fontWeight=FontWeight.Bold)
     Text("پس از بحث گروهی، نام کسی را که بیشترین رأی را گرفته وارد کن.",color=Color(0xFFB9BBD0),style=MaterialTheme.typography.bodySmall)
     OutlinedTextField(target,{target=it},modifier=Modifier.fillMaxWidth(),label={Text("نام بازیکن منتخب")},singleLine=true)
     Button(onClick={if(target in alive){val out=target;val rest=alive-out;alive=rest;note=out+" با رأی گروه حذف شد؛ نقش: "+roles[out];target="";resolve(rest);if(phase!="end"){phase="night";round++}}else note="نام یک بازیکن زنده را دقیق وارد کن."},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=SupraGlass.Ice)){Text("ثبت رأی و شروع شب")}
    }
    "end"->{
     Text(note,color=SupraGlass.Silver,fontWeight=FontWeight.Bold)
     Text("نقش مافیا: "+(mafia ?: "—"),color=Color.White)
     Button(onClick={phase="setup";names=emptyList();roles=emptyMap();alive=emptyList();target="";note="نام بازیکن‌ها را وارد کن."},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=SupraGlass.Ice)){Text("بازی جدید")}
    }
   }
   if(phase=="night"||phase=="day"){Divider(color=SupraGlass.Edge);Text("بازیکنان باقی‌مانده: "+alive.joinToString("، "),color=SupraGlass.Ice,style=MaterialTheme.typography.bodySmall)}
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
 Box(Modifier.width(132.dp).height(194.dp).background(SupraGlass.Glass,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){
  if(bitmap!=null) Image(bitmap!!,contentDescription="پوستر فیلم $title",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  else Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
   Icon(Icons.Default.Movie,null,tint=Color(0xFFD4D9E0),modifier=Modifier.size(32.dp))
   Text(if(failed)"پوستر موجود نیست" else "در حال دریافت…",color=Color(0xFFA8ADB8),style=MaterialTheme.typography.labelSmall)
  }
 }
}

@Composable
private fun FilmPosterTile(film: Film, onAskAi: (String) -> Unit) {
 Card(
  modifier = Modifier.fillMaxWidth().clickable {
   onAskAi("معرفی و نقد بدون اسپویل فیلم " + film.title + " را بنویس.")
  },
  colors = CardDefaults.cardColors(containerColor = FC),
  shape = RoundedCornerShape(14.dp)
 ) {
  Column(
   modifier = Modifier.fillMaxWidth().padding(9.dp),
   horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
   FilmPoster(film.title)
   Text(film.title, color = Color.White, fontWeight = FontWeight.Bold,
    style = MaterialTheme.typography.bodyMedium, maxLines = 2)
   Text(film.year.toString() + " • " + film.genre, color = FG,
    style = MaterialTheme.typography.labelSmall, maxLines = 2)
  }
 }
}

@Composable fun DiscoverDashboard(onClose:()->Unit,onAskAi:(String)->Unit,initialTab:String="فیلم امروز",standalone:Boolean=false){
 val ctx=LocalContext.current;var tab by remember{mutableStateOf(initialTab)};var query by remember{mutableStateOf("")};var mood by remember{mutableStateOf("همه")};var tracks by remember{mutableStateOf<List<Track>>(emptyList())};var loading by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var playing by remember{mutableStateOf<String?>(null)};var player by remember{mutableStateOf<MediaPlayer?>(null)}
 val scope=rememberCoroutineScope();val search=remember{TrackSearch()};val today=films[((System.currentTimeMillis()/86400000L)%films.size).toInt()]
 DisposableEffect(Unit){onDispose{player?.runCatching{stop()};player?.runCatching{release()}}}
 Column(Modifier.fillMaxSize().background(Color.Transparent).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(standalone)"موسیقی" else "سینما",color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(if(standalone)"جست‌وجو کن، گوش بده و هنرمندهای تازه پیدا کن" else "پوسترها، فیلم‌ها و پیشنهاد تماشا",color=FM)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=FG)}}
  if(!standalone) Row{listOf("فیلم امروز","جست‌وجوی فیلم").forEach{FilterChip(selected=tab==it,onClick={tab=it},label={Text(it)})}}
  if(tab=="فیلم امروز"){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
    item{FCard{Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){FilmPoster(today.title);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("انتخاب امروز",color=FG,fontWeight=FontWeight.Bold);Text(today.title,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(today.year.toString()+" • "+today.genre+" • "+today.duration+" دقیقه",color=FG,style=MaterialTheme.typography.labelSmall);Text(today.summary,color=Color.White,style=MaterialTheme.typography.bodySmall);Text(today.reason,color=FM,style=MaterialTheme.typography.bodySmall);Button(onClick={onAskAi("درباره فیلم "+today.title+" معرفی کامل بدون اسپویل و تحلیل سبک کارگردانی بنویس.")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("تحلیل بیشتر")}}}}}
    item{Text("بر اساس حال‌وهوا",color=FG,fontWeight=FontWeight.Bold);Row{listOf("همه","آرام","پرکشش","علمی‌تخیلی","درام").forEach{FilterChip(selected=mood==it,onClick={mood=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}}
    items(films.filter{mood=="همه"||it.genre.contains(mood)||(mood=="آرام"&&it.duration>=90)||(mood=="پرکشش"&&it.genre.contains("ماجراجویی"))}.chunked(2)){row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top){row.forEach{f->Box(Modifier.weight(1f)){FilmPosterTile(f,onAskAi)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
   }
  }else if(tab=="جست‌وجوی فیلم"){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
    item{FInput("نام فیلم یا ژانر",query,{query=it},Modifier.fillMaxWidth());Button(onClick={onAskAi("چند فیلم خوب دربارهٔ "+query+" معرفی کن و خلاصه و دلیل انتخابشان را بگو.")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("پیشنهاد با هوش مصنوعی")}}
    items(films.filter{query.isBlank()||it.title.contains(query,true)||it.genre.contains(query,true)}.chunked(2)){row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top){row.forEach{f->Box(Modifier.weight(1f)){FilmPosterTile(f,onAskAi)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
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

@Composable fun MusicDashboard(onClose:()->Unit,onAskAi:(String)->Unit){ MusicStudioDashboard(onClose,onAskAi) }

// Graphite and silver 3D feature gallery for EMA ASI PLATFORM.


@Composable
private fun FeatureMapTree(
    onClose: () -> Unit,
    modules: List<Triple<String, String, String>>
) {
    val context = LocalContext.current
    val appVersionInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val installedVersionName = appVersionInfo?.versionName ?: "نامشخص"
    val installedVersionCode = appVersionInfo?.let {
        if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else it.versionCode
    } ?: 0

    val silver = Color(0xFFD4D9E0)
    val muted = Color(0xFF9AA3AE)
    val panel = Color(0xFF171B20)
    val line = Color(0xFF626C78)

    data class Branch(val title: String, val items: List<String>, val status: String = "در کد برنامه")
    val branches = buildList {
        add(Branch(
            "مغز مرکزی و مسیر تصمیم‌گیری",
            listOf(
                "دریافت پیام یا درخواست کاربر",
                "تشخیص موضوع و انتخاب مسیر یا ابزار مرتبط",
                "بررسی اطلاعات لازم و پرسیدن سؤال در صورت ابهام",
                "نمایش خلاصه و گرفتن تأیید پیش از اقدام‌های حساس",
                "اجرای ابزار و ذخیره نتیجه در بخش مربوط؛ فقط در محدوده قابلیت‌های پیاده‌سازی‌شده"
            ),
            "هسته هماهنگ‌کننده"
        ))
        add(Branch(
            "حافظه، داده و امنیت",
            listOf(
                "گفتگو و ابزارهای هوش مصنوعی با سرویس و مدل تنظیم‌شده",
                "ذخیره محلی اطلاعات هر بخش در فضای داده مربوط به خودش",
                "نگهداری رمزگذاری‌شده کلید اتصال روی دستگاه",
                "پشتیبان‌گیری و بازیابی اطلاعات با امکانات موجود در برنامه"
            ),
            "زیرساخت برنامه"
        ))
        add(Branch(
            "تقویم چرخه قاعدگی",
            listOf(
                "ثبت تاریخ شروع آخرین پریود",
                "تنظیم طول چرخه و مدت خون‌ریزی",
                "ثبت علائم تجربه‌شده",
                "محاسبه تقریبی تاریخ شروع دوره بعد",
                "ذخیره اطلاعات به‌صورت محلی روی دستگاه"
            ),
            "قابلیت ثبت و برآورد محلی"
        ))
        modules.forEach { (title, description, status) ->
            add(Branch(title, listOf(description), status))
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Transparent).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NAEEM AI  /  LIVE FEATURE TREE", color = muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("درخت زنده ساختار نرم‌افزار", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("نقشه بخش‌ها، نقش‌ها و مسیر کار مغز هوشمند", color = muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = silver) }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            color = SupraGlass.Glass,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B4654)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("نسخه نصب‌شده", color = muted, style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(installedVersionName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text("کد نسخه " + installedVersionCode, color = silver, style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "نسخه قبلی: کد " + (installedVersionCode - 1).coerceAtLeast(0) + "  ·  نسخه بعدی: پس از انتشار ارتقای بعدی به‌روزرسانی می‌شود",
                    color = muted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "این نقشه از فهرست اصلی فضاهای برنامه ساخته می‌شود؛ با اضافه یا تغییر یک بخش در فهرست اصلی، شاخه آن هم در این صفحه به‌روز می‌شود.",
                    color = muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(12.dp))
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
                        Text("EMA ASI PLATFORM", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        Text("ریشه درخت · هسته هوشمند و هماهنگ‌کننده فضاها", color = muted, style = MaterialTheme.typography.bodySmall)
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
                            }
                            branch.items.forEach { item ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("└", color = line)
                                    Text(item, color = Color(0xFFD0D5DC), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                }
                            }
                            Surface(color = Color(0xFF252D37), shape = RoundedCornerShape(9.dp)) {
                                Text(branch.status, color = muted, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "وضعیت هر بخش از توضیحات ثبت‌شده در فهرست اصلی برنامه می‌آید. «فعال» به معنی اتصال یا امکان موجود است و عبارت‌های «در حال توسعه» یا «نیازمند تنظیم» به معنی قابلیت کامل و آزموده‌شده نیست.",
                    color = muted,
                    style = MaterialTheme.typography.labelSmall
                )
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
        Modifier.fillMaxSize().background(Color.Transparent).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
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
                Text("رمز عبورت را داخل EMA ASI PLATFORM وارد نکن. اتصال مستقیم اینستاگرام از مسیر رسمی Meta در حال آماده‌سازی است و پس از ثبت اپ و مجوزها فعال می‌شود. فعلاً می‌توانی آمار Insights را وارد کنی و تحلیل بگیری.", color = FM, style = MaterialTheme.typography.bodySmall)
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
