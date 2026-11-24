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
 Column(Modifier.fillMaxSize().background(FutureBg).padding(horizontal=16.dp)){
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
   }
  }else{
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)){
    item{
     Text(selected!!.title,color=FutureText,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
     Text(selected!!.subtitle,color=FutureMuted)
     Spacer(Modifier.height(8.dp))
     Card(colors=CardDefaults.cardColors(containerColor=FutureCard)){Text(selected!!.instruction,Modifier.padding(14.dp),color=FutureMuted)}
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
