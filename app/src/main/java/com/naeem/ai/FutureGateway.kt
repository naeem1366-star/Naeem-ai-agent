package com.naeem.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class FutureMode(val title:String,val subtitle:String,val instruction:String,val symbol:String)
private val futureModes = listOf(
 FutureMode("شورای مخالفان","بحث چندصدایی و رأی نهایی","در نقش شورایی متشکل از موافق، مخالف، بدبین، آینده‌نگر و داور بحث کن. استدلال‌ها مستقل باشند و در پایان رأی و جمع‌بندی بده.","⚖"),
 FutureMode("نسخه دوم خودم","تصمیم از زاویه نگاه خودت","مثل مشاور شخصی من فکر کن، اما فقط بر اساس اطلاعاتی که در همین گفتگو می‌دهم. اگر شناخت کافی نداری سؤال بپرس و ادعای حافظه‌ای که در اختیار نداری نکن.","◉"),
 FutureMode("جهان‌های موازی","سه مسیر احتمالی برای یک تصمیم","سه سناریو بساز: انجام دادن، انجام ندادن و مسیر سوم. پیامدها، فرصت‌ها، ریسک‌ها و فرضیات هرکدام را جدا کن و پیش‌بینی قطعی نکن.","∞"),
 FutureMode("باستان‌شناس ذهن","کشف ریشه‌ها و ارتباط‌های پنهان","متن‌ها و ایده‌های ارائه‌شده را بررسی کن؛ مضمون‌های تکراری، ارتباط‌های پنهان، تغییر دیدگاه و پرسش‌های بی‌پاسخ را پیدا کن. فقط از داده‌های موجود استفاده کن.","⌘"),
 FutureMode("پرونده‌های حل‌نشده","شواهد، فرضیه‌ها و تناقض‌ها","موضوع را مثل پرونده تحقیقی بررسی کن. دانسته‌ها، شواهد، فرضیه‌ها، تناقض‌ها و اطلاعات مفقود را جدا کن. منبع یا جست‌وجوی انجام‌نشده جعل نکن.","⌕"),
 FutureMode("دستگاه ترکیب ایده‌ها","ترکیب دنیاهای نامرتبط","عناصر داده‌شده را غیرمنتظره ترکیب کن و چند مفهوم تازه برای محصول، خدمت، فضا یا کسب‌وکار بساز؛ همراه نام، کاربرد، مخاطب و نمونه اولیه.","✳"),
 FutureMode("زندگی در حالت مأموریت","تبدیل هدف به مرحله و چالش","هدف را به بازی مأموریتی تبدیل کن: مراحل کوچک، مأموریت اصلی، چالش جانبی، معیار پیشرفت و پاداش نمادین طراحی کن. برنامه واقع‌بینانه باشد.","➤"),
 FutureMode("پیام از آینده","نامه برای خودت در آینده","با اطلاعاتی که می‌دهم نامه‌ای برای خود آینده‌ام بنویس و هدف‌ها، نگرانی‌ها و امیدهای امروز را ثبت کن. زمان‌بندی یا اعلان خودکار ادعا نکن.","✉"),
 FutureMode("کالبدشکافی ایده","نقد سخت‌گیرانه اما منصفانه","فرض‌های پنهان، نقاط شکست، هزینه‌ها، موانع اجرا و رقبا را بررسی کن؛ سپس نسخه اصلاح‌شده و آزمایش کم‌هزینه پیشنهاد بده.","⌁"),
 FutureMode("نقشه دانش شخصی","شبکه دانسته‌ها و ارتباط‌ها","موضوعات ارائه‌شده را به شبکه مفهومی تبدیل کن؛ مفاهیم اصلی، ارتباط‌ها، پیش‌نیازها، خلأهای دانشی و مسیر یادگیری را نشان بده.","⌘"),
 FutureMode("آشوب خلاق","چالش عجیب برای ایده‌پردازی","تمرین خلاقانه‌ای غیرمعمول متناسب با موضوع طراحی کن؛ با محدودیت عجیب، زمان پیشنهادی، نمونه شروع و معیار ارزیابی. از کلیشه دوری کن.","✺"),
 FutureMode("اتاق فرمان","نمای کلی هدف‌ها و اقدام‌ها","اطلاعات ارائه‌شده را به داشبورد متنی تبدیل کن: وضعیت، هدف‌ها، پروژه‌ها، موانع، تصمیم‌های معوق و سه اقدام بعدی. داده ناموجود را نامشخص بنویس.","▦")
)
private fun futureDescription(mode: FutureMode): String = when (mode.title) {
 "شورای مخالفان" -> "اینجا یک پاسخ ساده نمی‌گیری. مسئله‌ات وارد گفت‌وگوی چند شخصیت با منافع و زاویه دید متفاوت می‌شود: موافق فرصت‌ها را می‌بیند، مخالف ایرادها را پیدا می‌کند، بدبین خطرها را گوشزد می‌کند، آینده‌نگر پیامدهای دورتر را می‌سنجد و داور استدلال‌ها را جمع‌بندی می‌کند. مناسب تصمیم‌های مهم، ایده‌های تجاری و بررسی چندجانبه یک موضوع است."
 "نسخه دوم خودم" -> "این تجربه تلاش می‌کند نقش یک همتای فکری را بازی کند؛ کسی که مسئله را با ارزش‌ها و اولویت‌هایی که خودت توضیح می‌دهی می‌سنجد. می‌توانی از او بخواهی بین چند انتخاب مقایسه کند یا از زاویه نگاه خودت به یک تصمیم نگاه کند. هرجا اطلاعات کافی نباشد باید سؤال بپرسد، نه اینکه شناخت خیالی از تو بسازد."
 "جهان‌های موازی" -> "قبل از انتخاب، سه مسیر ممکن را کنار هم ببین. مسیر اول اجرای تصمیم فعلی، مسیر دوم کنار گذاشتن آن و مسیر سوم یک راه جایگزین است. برای هر مسیر پیامدهای کوتاه‌مدت و بلندمدت، فرصت‌ها، هزینه‌ها، ریسک‌ها و فرض‌های پنهان بررسی می‌شوند. این شبیه‌سازی پیش‌گویی نیست؛ ابزاری برای فکر کردن بهتر است."
 "باستان‌شناس ذهن" -> "ایده‌ها و نوشته‌هایت را مثل لایه‌های یک آرشیو بررسی می‌کند تا ریشه‌های مشترک، موضوعات تکرارشونده، تغییر دیدگاه‌ها و ارتباط‌هایی را پیدا کند که شاید در نگاه اول دیده نشوند. می‌توانی چند یادداشت یا شرح ایده را وارد کنی و از آن بخواهی نقشه‌ای از پیوندهای میانشان بسازد."
 "پرونده‌های حل‌نشده" -> "هر مسئله به یک پرونده تبدیل می‌شود: چه چیزهایی می‌دانیم، چه شواهدی داریم، کدام بخش‌ها متناقض‌اند و چه فرضیه‌هایی می‌توانند توضیح‌دهنده باشند؟ در پایان فهرستی از اطلاعات مفقود و قدم بعدی تحقیق می‌گیری. این بخش بین واقعیت، حدس و سؤال باز تفاوت می‌گذارد."
 "دستگاه ترکیب ایده‌ها" -> "دو یا چند دنیای ظاهراً نامرتبط را به هم وصل کن؛ مثلاً معماری ژاپنی، پوشاک خیابانی و مبلمان. خروجی می‌تواند چند کانسپت تازه برای محصول، فضا، خدمت یا کسب‌وکار باشد؛ همراه با مخاطب، کاربرد، ویژگی متمایز و پیشنهاد نمونه اولیه برای آزمایش."
 "زندگی در حالت مأموریت" -> "یک هدف بزرگ را به مأموریت‌های کوچک و قابل انجام تبدیل می‌کند. مرحله اصلی، چالش‌های جانبی، معیار تکمیل، زمان پیشنهادی و پاداش نمادین تعریف می‌شوند تا پیشرفت قابل مشاهده باشد. اینجا قرار نیست فقط فهرست کارها بسازی؛ قرار است برای حرکت کردن یک مسیر بازی‌وار داشته باشی."
 "پیام از آینده" -> "برای خودت در آینده نامه بنویس؛ از جایی که امروز ایستاده‌ای، چیزی که می‌خواهی به آن برسی، نگرانی‌ها و امیدهایت. می‌توانی پرسش‌هایی بگذاری که هنگام بازخوانی از خودت بپرسی. این نسخه متن نامه را می‌سازد؛ ارسال خودکار یا یادآوری زمان‌دار فقط در صورت افزودن قابلیت زمان‌بندی جداگانه ممکن است."
 "کالبدشکافی ایده" -> "ایده‌ات را به جای تعریف و تمجید، زیر ذره‌بین بگذار. فرض‌های پنهان، هزینه‌های واقعی، موانع اجرا، نقاط شکست، رقبا و دلیل‌هایی که ممکن است مخاطب استقبال نکند بررسی می‌شوند. بعد از نقد، نسخه اصلاح‌شده و یک آزمایش کوچک و کم‌هزینه برای سنجش ایده پیشنهاد می‌شود."
 "نقشه دانش شخصی" -> "موضوعات و دانسته‌ها را به یک نقشه مفهومی متصل تبدیل می‌کند. مفاهیم اصلی، رابطه میان آن‌ها، پیش‌نیازهای یادگیری، بخش‌های نامعلوم و مسیر پیشنهادی مطالعه مشخص می‌شوند. این تجربه برای یادگیری یک حوزه تازه یا دیدن ارتباط میان چند رشته کاربرد دارد."
 "آشوب خلاق" -> "برای شکستن عادت‌های فکری، یک چالش غیرمعمول می‌سازد؛ محدودیت‌های عجیب، ترکیب‌های نامنتظر و تمرین‌هایی که تو را مجبور می‌کنند از مسیر همیشگی خارج شوی. می‌توانی موضوع طراحی، کسب‌وکار، نوشتن یا حل مسئله بدهی و چند جرقه تازه بگیری."
 else -> "یک نمای کلی از وضعیت و حرکت بعدی بساز. هدف‌ها، پروژه‌های فعال، تصمیم‌های معوق، موانع و اقدام‌های بعدی در یک تصویر منظم کنار هم قرار می‌گیرند. فقط اطلاعاتی که ارائه کرده‌ای مبنا قرار می‌گیرد و موارد نامعلوم به‌روشنی مشخص می‌شوند."
}
private val FutureBg=Color(0xFF090C12)
private val FutureCard=Color(0xFF171D27)
private val FutureText=Color(0xFFE7EBF1)
private val FutureMuted=Color(0xFFA5AFBD)

@Composable
fun FutureGatewayScreen(onClose:()->Unit,baseUrl:String,model:String,apiKey:String){
 val scope= rememberCoroutineScope()
 var selected by remember { mutableStateOf<FutureMode?>(null) }
 var input by remember { mutableStateOf("") }
 var answer by remember { mutableStateOf("") }
 var error by remember { mutableStateOf("") }
 var busy by remember { mutableStateOf(false) }
 var showCreator by remember { mutableStateOf(false) }
 Column(Modifier.fillMaxSize().background(FutureBg).safeDrawingPadding().padding(horizontal=16.dp)){
  Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick={if(selected==null) onClose() else {selected=null;answer="";error=""}}){Icon(Icons.Default.ArrowBack,null,tint=FutureText)}
   Column(Modifier.weight(1f)){
    Text("به آینده وارد شو",color=FutureText,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text("آزمایشگاه تجربه‌های فکری و خلاق",color=FutureMuted,style=MaterialTheme.typography.bodySmall)
   }
   Icon(Icons.Default.AutoAwesome,null,tint=Color(0xFFC4B4E8))
  }
  if(selected==null){
   Text("۱۲ درگاه برای دیدن مسئله‌ها از زاویه‌ای تازه. یکی را انتخاب کن.",color=FutureMuted,modifier=Modifier.padding(vertical=8.dp))
   LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp)){
    items(futureModes){ mode->
     Row(Modifier.fillMaxWidth().background(FutureCard,RoundedCornerShape(16.dp)).clickable{selected=mode;answer="";error=""}.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
      Box(Modifier.size(46.dp).background(Color(0xFF293142),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Text(mode.symbol,color=Color(0xFFD6C9F1),style=MaterialTheme.typography.headlineSmall)}
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
       Text(mode.title,color=FutureText,fontWeight=FontWeight.SemiBold)
       Text(mode.subtitle,color=FutureMuted,style=MaterialTheme.typography.bodySmall)
      }
      Text("‹",color=FutureMuted,style=MaterialTheme.typography.headlineMedium)
     }
    }
    item {
     Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=10.dp).background(Color(0xFF111722),RoundedCornerShape(17.dp)).clickable { showCreator = !showCreator }.padding(16.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
      Text("درباره سازنده",color=Color(0xFFD6C9F1),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
      Text("نعیم دهقانی  •  ایده‌پرداز و توسعه‌دهنده",color=FutureText,fontWeight=FontWeight.SemiBold)
      Text("این فضا از نگاه یک سازنده میان‌رشته‌ای شکل گرفته؛ کسی که طراحی، ساخت برند، محصول و تجربه را به هم پیوند می‌دهد.",color=FutureMuted,style=MaterialTheme.typography.bodySmall)
      if(showCreator) {
       Text("نعیم دهقانی طراح، ایده‌پرداز و کارآفرین خلاق است. مسیر حرفه‌ای او از ساخت و توسعه برند و فعالیت در فضای مد و سبک زندگی آغاز شده و به حوزه‌هایی مانند طراحی محصول، مبلمان، هویت بصری، فضاهای فرهنگی و تجربه‌های خلاق گسترش یافته است. او در پروژه‌های مختلف تلاش کرده میان زیبایی‌شناسی، نیاز واقعی مخاطب و امکان اجرا تعادل برقرار کند. علاقه‌اش به کنار هم گذاشتن رشته‌های متفاوت، آزمودن ایده‌های نامتعارف و تبدیل فکر به محصول و تجربه، مبنای شکل‌گیری این بخش بوده است. «به آینده وارد شو» با ایده‌پردازی و توسعه نعیم دهقانی ساخته شده تا فضایی برای پرسیدن سؤال‌های بهتر، دیدن مسیرهای تازه و تبدیل ایده‌ها به اقدام فراهم کند.",color=FutureText,style=MaterialTheme.typography.bodyMedium)
       Spacer(Modifier.height(2.dp))
       Text("ایده‌پردازی، طراحی و توسعه: نعیم دهقانی",color=Color(0xFFD6C9F1),fontWeight=FontWeight.SemiBold)
      } else Text("برای خواندن معرفی کامل لمس کن",color=FutureMuted,style=MaterialTheme.typography.labelSmall)
     }
    }
   }
  }else{
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)){
    item{
     Text(selected!!.title,color=FutureText,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
     Text(selected!!.subtitle,color=FutureMuted)
     Spacer(Modifier.height(8.dp))
     Card(colors=CardDefaults.cardColors(containerColor=FutureCard),shape=RoundedCornerShape(16.dp)){
      Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       Text("این تجربه چه کاری انجام می‌دهد؟",color=Color(0xFFD6C9F1),fontWeight=FontWeight.SemiBold)
       Text(futureDescription(selected!!),color=FutureText,style=MaterialTheme.typography.bodyMedium)
       Divider(color=Color(0xFF303947))
       Text("روش کار",color=Color(0xFFD6C9F1),fontWeight=FontWeight.SemiBold)
       Text(selected!!.instruction,color=FutureMuted,style=MaterialTheme.typography.bodySmall)
      }
     }
    }
    if(busy)item{Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp));Text("در حال ساخت تجربه…",color=FutureMuted)}}
    if(error.isNotBlank())item{Text(error,color=Color(0xFFFF9999))}
    if(answer.isNotBlank())item{Card(colors=CardDefaults.cardColors(containerColor=FutureCard),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(15.dp)){Text("نتیجه تجربه",color=Color(0xFFD6C9F1),fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(8.dp));Text(answer,color=FutureText)}}}
   }
   OutlinedTextField(value=input,onValueChange={input=it},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=5,placeholder={Text("موضوع، تصمیم یا ایده‌ات را بنویس…")},shape=RoundedCornerShape(15.dp))
   Button(onClick={
    val userText=input.trim();val mode=selected!!
    if(userText.isNotBlank()&&!busy){
     busy=true;error="";answer=""
     scope.launch{
      try{
       val prompt="تو در بخش به آینده وارد شو هستی. فقط فارسی روان بنویس.\n\nتجربه: "+mode.title+"\nدستور: "+mode.instruction+"\n\nدرخواست کاربر:\n"+userText+"\n\nپاسخ دقیق، خلاق، ساختارمند و کاربردی بده. واقعیت، فرضیه و پیشنهاد را جدا کن و کاری را که انجام نداده‌ای ادعا نکن."
       answer=ChatClient().send(baseUrl,model,apiKey,prompt)
      }catch(e:Exception){error=e.message?:"ساخت تجربه ناموفق بود."}
      finally{busy=false}
     }
    }
   },enabled=!busy&&input.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=8.dp,bottom=14.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFD9DDE5),contentColor=Color(0xFF151922))){
    if(busy)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else Icon(Icons.Default.Send,null)
    Spacer(Modifier.width(8.dp));Text(if(busy)"در حال پردازش…" else "شروع تجربه")
   }
  }
 }
}
