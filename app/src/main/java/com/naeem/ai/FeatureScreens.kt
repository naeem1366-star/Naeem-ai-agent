package com.naeem.ai

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private val FG=Color(0xFFD7B77D)
private val FB=Color(0xFF111318)
private val FC=Color(0xFF20232B)
private val FM=Color(0xFFA8ADB8)

@Composable
fun FeatureHub(onClose:()->Unit,onFinance:()->Unit,onHealth:()->Unit,onGames:()->Unit,onDiscover:()->Unit,onMemory:()->Unit,onAlbum:()->Unit,onSettings:()->Unit){
 Column(Modifier.fillMaxSize().background(FB).padding(20.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("NAEEM AI",color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("مرکز ابزارهای شخصی تو",color=FM)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=FG)}}
  Text("دستیار و مدیریت",color=FG,fontWeight=FontWeight.Bold)
  HubItem("دفتر مالی","هزینه‌ها، درآمدها و گزارش ماهانه",Icons.Default.AccountBalanceWallet,onFinance)
  HubItem("حافظهٔ هوشمند","یادداشت‌ها و اطلاعات ذخیره‌شده",Icons.Default.Bookmark,onMemory)
  HubItem("آلبوم تصاویر","تصاویر ساخته‌شده با هوش مصنوعی",Icons.Default.Collections,onAlbum)
  Spacer(Modifier.height(12.dp));Text("زندگی و تندرستی",color=FG,fontWeight=FontWeight.Bold)
  HubItem("سلامت و تغذیه","BMI، کالری، وعده‌ها، آب و فعالیت",Icons.Default.Favorite,onHealth)
  Spacer(Modifier.height(12.dp));Text("سرگرمی و کشف",color=FG,fontWeight=FontWeight.Bold)
  HubItem("بازی‌ها","بازی گروهی کلمه‌ای و چالش ذهن",Icons.Default.SportsEsports,onGames)
  HubItem("فیلم و موسیقی","پیشنهاد روز فیلم و جست‌وجوی آهنگ",Icons.Default.Movie,onDiscover)
  Spacer(Modifier.weight(1f))
  OutlinedButton(onClick=onSettings,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Settings,null);Text("تنظیمات و اتصال")}
 }
}
@Composable private fun HubItem(title:String,sub:String,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit){
 Card(Modifier.fillMaxWidth().padding(top=7.dp).clickable{click()},colors=CardDefaults.cardColors(containerColor=FC),shape=RoundedCornerShape(16.dp)){
  Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=FG,modifier=Modifier.size(28.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,color=Color.White,fontWeight=FontWeight.SemiBold);Text(sub,color=FM,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronLeft,null,tint=FG)}
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
@Composable fun HealthDashboard(onClose:()->Unit){
 val ctx=LocalContext.current;val store=remember{HStore(ctx)}
 var height by remember{mutableStateOf(store.get("h"))};var weight by remember{mutableStateOf(store.get("w"))};var age by remember{mutableStateOf(store.get("age"))}
 var sex by remember{mutableStateOf(store.get("sex","مرد"))};var activity by remember{mutableStateOf(store.get("activity","متوسط"))};var goal by remember{mutableStateOf(store.get("goal","حفظ وزن"))}
 var water by remember{mutableIntStateOf(store.get("water","0").toIntOrNull()?:0)}
 var name by remember{mutableStateOf("")};var kcal by remember{mutableStateOf("")};var logs by remember{mutableStateOf(store.logs())}
 var foodSearch by remember{mutableStateOf("")};var grams by remember{mutableStateOf("100")};var selectedFood by remember{mutableStateOf<FoodInfo?>(null)};var meal by remember{mutableStateOf("ناهار")}
 val h=height.toDoubleOrNull()?.div(100.0);val w=weight.toDoubleOrNull();val years=age.toIntOrNull()
 val bmi=if(h!=null&&h>0&&w!=null&&w>0)w/(h*h)else null
 val bmr=if(h!=null&&h>0&&w!=null&&w>0&&years!=null&&years>0)10*w+6.25*(h*100)-5*years+(if(sex=="مرد")5 else -161) else null
 val tdee=bmr?.times(when(activity){"کم‌تحرک"->1.2;"سبک"->1.375;"زیاد"->1.725;else->1.55})?.toInt()
 val target=tdee?.let{when(goal){"کاهش وزن"->it-350;"افزایش وزن"->it+300;else->it}}
 val today=java.util.Calendar.getInstance().apply{set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
 val todayLogs=logs.filter{it.at>=today};val eaten=todayLogs.filter{it.type=="غذا"}.sumOf{it.kcal};val burned=todayLogs.filter{it.type=="فعالیت"}.sumOf{it.kcal}
 val nf=remember{NumberFormat.getNumberInstance(Locale.US)}
 Column(Modifier.fillMaxSize().background(FB).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("سلامت و تغذیه",color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("داشبورد بدن و انرژی",color=FM)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=FG)}}
  LazyColumn(verticalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(bottom=24.dp)){
   item{FCard{
    Text("پروفایل بدن",color=FG,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FInput("قد cm",height,{height=it.filter(Char::isDigit)},Modifier.weight(1f));FInput("وزن kg",weight,{weight=it.filter{c->c.isDigit()||c=='.'}},Modifier.weight(1f));FInput("سن",age,{age=it.filter(Char::isDigit)},Modifier.weight(1f))}
    Row{listOf("مرد","زن").forEach{FilterChip(selected=sex==it,onClick={sex=it},label={Text(it)})}}
    Text("فعالیت روزانه",color=FM);Row{listOf("کم‌تحرک","سبک","متوسط","زیاد").forEach{FilterChip(selected=activity==it,onClick={activity=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}
    Text("هدف",color=FM);Row{listOf("کاهش وزن","حفظ وزن","افزایش وزن").forEach{FilterChip(selected=goal==it,onClick={goal=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}
    Button(onClick={store.put("h",height);store.put("w",weight);store.put("age",age);store.put("sex",sex);store.put("activity",activity);store.put("goal",goal)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("ذخیرهٔ پروفایل")}
   }}
   item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    FMetric("BMI",bmi?.let{String.format(Locale.US,"%.1f",it)}?:"—",bmi?.let{when{it<18.5->"کمبود وزن";it<25->"محدوده معمول";it<30->"اضافه‌وزن";else->"بالا"}}?:"قد و وزن را وارد کن",Modifier.weight(1f))
    FMetric("کالری پایه",bmr?.toInt()?.toString()?:"—","BMR روزانه",Modifier.weight(1f))
   };Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=7.dp)){FMetric("هدف کالری",target?.toString()?:"—","برآورد روزانه",Modifier.weight(1f));FMetric("مصرف امروز",eaten.toString(),"فعالیت: "+burned+" kcal",Modifier.weight(1f))}
    if(bmi!=null)Text("BMI فقط شاخص غربالگری است، نه تشخیص پزشکی.",color=FM,style=MaterialTheme.typography.labelSmall)
   }
   item{FCard{
    Text("بانک غذایی؛ محاسبه بر اساس گرم",color=FG,fontWeight=FontWeight.Bold)
    OutlinedTextField(foodSearch,{foodSearch=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("جست‌وجوی ماده غذایی")},leadingIcon={Icon(Icons.Default.Search,null)})
    foodBank.filter{it.name.contains(foodSearch,true)}.forEach{food->TextButton(onClick={selectedFood=food}){Text(food.name+" • "+food.kcal.toInt()+" kcal / ۱۰۰ گرم",color=if(selectedFood==food)FG else Color.White)}}
    if(selectedFood!=null){
     Text("انتخاب: "+selectedFood!!.name,color=FG,fontWeight=FontWeight.SemiBold)
     FInput("مقدار به گرم",grams,{grams=it.filter{c->c.isDigit()||c=='.'}},Modifier.fillMaxWidth())
     Row{listOf("صبحانه","ناهار","شام","میان‌وعده").forEach{FilterChip(selected=meal==it,onClick={meal=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}
     val g=grams.toDoubleOrNull()?:0.0;val factor=g/100.0
     Text("انرژی: "+(selectedFood!!.kcal*factor).toInt()+" kcal • پروتئین: "+String.format(Locale.US,"%.1f",selectedFood!!.protein*factor)+" g",color=FG)
     Text("کربوهیدرات: "+String.format(Locale.US,"%.1f",selectedFood!!.carbs*factor)+" g • چربی: "+String.format(Locale.US,"%.1f",selectedFood!!.fat*factor)+" g",color=FM,style=MaterialTheme.typography.bodySmall)
     Button(onClick={store.add(HLog(selectedFood!!.name,(selectedFood!!.kcal*factor).roundToInt(),"غذا • "+meal,System.currentTimeMillis(),selectedFood!!.protein*factor,selectedFood!!.carbs*factor,selectedFood!!.fat*factor));logs=store.logs();selectedFood=null;grams="100"},enabled=g>0,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("ثبت در دفترچه غذایی")}
    }
   }}
   item{FCard{Text("ثبت خوراکی و کالری",color=FG,fontWeight=FontWeight.Bold);FInput("نام غذا یا وعده",name,{name=it},Modifier.fillMaxWidth());FInput("کالری kcal",kcal,{kcal=it.filter(Char::isDigit)},Modifier.fillMaxWidth());Button(onClick={val c=kcal.toIntOrNull()?:0;if(name.isNotBlank()&&c>0){store.add(HLog(name,c,"غذا",System.currentTimeMillis()));logs=store.logs();name="";kcal=""}},enabled=name.isNotBlank()&&kcal.toIntOrNull()!=null,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("ثبت وعده")};Text("در این نسخه کالری را وارد می‌کنی؛ بانک غذایی و محاسبهٔ خودکار بر اساس گرم در مرحلهٔ بعد به این صفحه متصل می‌شود.",color=FM,style=MaterialTheme.typography.labelSmall)}}
   item{FCard{Text("آب روزانه",color=FG,fontWeight=FontWeight.Bold);Text(water.toString()+" از ۸ لیوان",color=Color.White,style=MaterialTheme.typography.titleLarge);LinearProgressIndicator(progress={(water/8f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=FG,trackColor=FB);Row{OutlinedButton(onClick={water=(water-1).coerceAtLeast(0);store.put("water",water.toString())}){Text("−۱")};Button(onClick={water++;store.put("water",water.toString())},colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("+ یک لیوان")}}}}
   item{FCard{
    var actName by remember{mutableStateOf("پیاده‌روی")};var actKcal by remember{mutableStateOf("")}
    Text("فعالیت و پیاده‌روی",color=FG,fontWeight=FontWeight.Bold);FInput("نوع فعالیت",actName,{actName=it},Modifier.fillMaxWidth());FInput("کالری سوزانده‌شده",actKcal,{actKcal=it.filter(Char::isDigit)},Modifier.fillMaxWidth())
    Button(onClick={val c=actKcal.toIntOrNull()?:0;if(c>0){store.add(HLog(actName,c,"فعالیت",System.currentTimeMillis()));logs=store.logs();actKcal=""}},enabled=actKcal.toIntOrNull()!=null,colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("ثبت فعالیت")}
   }}
   item{FCard{Text("پیشنهاد تغذیه‌ای",color=FG,fontWeight=FontWeight.Bold);val tips=when(goal){"کاهش وزن"->listOf("در هر وعده پروتئین و سبزیجات داشته باش.","نوشیدنی شیرین را با آب جایگزین کن.","کاهش کالری را تدریجی نگه دار.");"افزایش وزن"->listOf("از میان‌وعده‌های مغذی استفاده کن.","پروتئین را در وعده‌ها پخش کن.","افزایش انرژی را تدریجی انجام بده.");else->listOf("وعده‌ها را متعادل و متنوع نگه دار.","در طول روز آب کافی بنوش.","پروتئین، سبزیجات و غلات کامل را ترکیب کن.")};tips.forEach{Text("• "+it,color=Color.White,style=MaterialTheme.typography.bodySmall)}}}
   item{Text("ثبت‌های امروز",color=FG,fontWeight=FontWeight.Bold)}
   items(todayLogs.reversed(),key={it.at}){log->Card(colors=CardDefaults.cardColors(containerColor=FC)){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(log.name,color=Color.White);Text(log.type,color=FM,style=MaterialTheme.typography.labelSmall)};Text(log.kcal.toString()+" kcal",color=FG);IconButton(onClick={store.remove(log.at);logs=store.logs()}){Icon(Icons.Default.Delete,null,tint=Color(0xFFFF9A91))}}}}
  }
 }
}
@Composable private fun FMetric(title:String,value:String,sub:String,modifier:Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=FC),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(12.dp)){Text(title,color=FM,style=MaterialTheme.typography.labelSmall);Text(value,color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(sub,color=FM,style=MaterialTheme.typography.labelSmall)}}}

@Composable fun GamesHub(onClose:()->Unit){
 var team1 by remember{mutableStateOf("تیم طلایی")};var team2 by remember{mutableStateOf("تیم مشکی")};var score1 by remember{mutableIntStateOf(0)};var score2 by remember{mutableIntStateOf(0)};var category by remember{mutableStateOf("ترکیبی")};var word by remember{mutableStateOf("برای شروع کلمهٔ بعدی را بزن")};var round by remember{mutableIntStateOf(0)}
 val decks=remember{mapOf("ترکیبی" to listOf("چتر","فضانورد","آشپز","ترافیک","شطرنج","کوهستان","دندان‌پزشک","هواپیما","کارآگاه","کتابخانه","آتش‌نشان","ماهیگیری","ربات","سینما","دوچرخه","آبشار","جادوگر","بستنی"),"سخت" to listOf("پارادوکس","ناخودآگاه","جاذبه","معماری","دیپلماسی","فرسایش","کهکشان","نوستالژی","الگوریتم"),"سینما" to listOf("کارگردان","اسکار","دوربین","فیلمنامه","اکران","جلوه‌های ویژه","سکانس","بازیگر"))}
 Column(Modifier.fillMaxSize().background(FB).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("باشگاه بازی",color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("بازی‌های دورهمی و چالش ذهن",color=FM)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=FG)}}
  LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=25.dp)){
   item{FCard{
    Text("حدس کلمه؛ بازی تیمی",color=FG,fontWeight=FontWeight.Bold)
    Row{listOf("ترکیبی","سخت","سینما").forEach{FilterChip(selected=category==it,onClick={category=it},label={Text(it)})}}
    Box(Modifier.fillMaxWidth().background(Color(0xFF302B22),RoundedCornerShape(15.dp)).padding(22.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("کلمهٔ دور",color=FM);Text(word,color=FG,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("دور "+round,color=FM)}}
    Button(onClick={word=decks[category].orEmpty().random();round++},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("کلمهٔ بعدی")}
    Text("کلمه را با توضیح یا پانتومیم منتقل کن؛ گفتن خود کلمه و هم‌خانواده‌اش ممنوع!",color=FM,style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){FInput("نام تیم اول",team1,{team1=it});Text(score1.toString()+" امتیاز",color=FG);OutlinedButton(onClick={score1++},modifier=Modifier.fillMaxWidth()){Text("+ امتیاز")}};Column(Modifier.weight(1f)){FInput("نام تیم دوم",team2,{team2=it});Text(score2.toString()+" امتیاز",color=FG);OutlinedButton(onClick={score2++},modifier=Modifier.fillMaxWidth()){Text("+ امتیاز")}}}
    OutlinedButton(onClick={score1=0;score2=0;round=0;word="برای شروع کلمهٔ بعدی را بزن"},modifier=Modifier.fillMaxWidth()){Text("شروع دوباره")}
   }}
   item{FCard{
    var a by remember{mutableIntStateOf(7)};var b by remember{mutableIntStateOf(8)};var answer by remember{mutableStateOf("")};var result by remember{mutableStateOf("")};var points by remember{mutableIntStateOf(0)}
    Text("چالش محاسبهٔ سریع",color=FG,fontWeight=FontWeight.Bold);Text(a.toString()+" × "+b.toString()+" = ؟",color=Color.White,style=MaterialTheme.typography.headlineLarge);FInput("پاسخ",answer,{answer=it.filter(Char::isDigit)},Modifier.fillMaxWidth())
    Button(onClick={if(answer.toIntOrNull()==a*b){points++;result="درست بود! امتیاز "+points}else result="پاسخ درست "+(a*b)+" بود";a=(2..12).random();b=(2..12).random();answer=""},colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("بررسی و سؤال بعدی")};Text(result,color=FM)
   }}
   item{FCard{
    var seq by remember{mutableStateOf(listOf<Int>())};var chosen by remember{mutableStateOf("")};var info by remember{mutableStateOf("الگوی رنگی را ببین و ترتیبش را تکرار کن.")};val names=listOf("قرمز","آبی","سبز","زرد");val colors=listOf(Color(0xFFE66B62),Color(0xFF6C9FE6),Color(0xFF65B98A),Color(0xFFE3BD61))
    Text("حافظهٔ رنگ‌ها",color=FG,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){colors.forEachIndexed{i,c->Box(Modifier.weight(1f).height(48.dp).background(c,RoundedCornerShape(10.dp)).clickable{chosen+=i.toString();if(seq.isNotEmpty()&&chosen.length==seq.size){val ok=chosen==seq.joinToString("");info=if(ok)"آفرین! مرحلهٔ بعد را بساز." else "اشتباه شد؛ دوباره الگو را ببین.";if(ok)seq=seq+ (0..3).random();chosen=""}},contentAlignment=Alignment.Center){Text(names[i],color=Color.Black,fontWeight=FontWeight.Bold)}}}
    Button(onClick={seq=listOf((0..3).random(),(0..3).random());chosen="";info="الگو: "+seq.joinToString("، "){names[it]}},colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("نمایش الگو")}
    Text(info,color=FM,style=MaterialTheme.typography.bodySmall)
   }}
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
@Composable fun DiscoverDashboard(onClose:()->Unit,onAskAi:(String)->Unit){
 val ctx=LocalContext.current;var tab by remember{mutableStateOf("فیلم امروز")};var query by remember{mutableStateOf("")};var mood by remember{mutableStateOf("همه")};var tracks by remember{mutableStateOf<List<Track>>(emptyList())};var loading by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var playing by remember{mutableStateOf<String?>(null)};var player by remember{mutableStateOf<MediaPlayer?>(null)}
 val scope=rememberCoroutineScope();val search=remember{TrackSearch()};val today=films[((System.currentTimeMillis()/86400000L)%films.size).toInt()]
 DisposableEffect(Unit){onDispose{player?.runCatching{stop()};player?.runCatching{release()}}}
 Column(Modifier.fillMaxSize().background(FB).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("اتاق کشف",color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("فیلم و موسیقی متناسب با حال‌وهوایت",color=FM)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=FG)}}
  Row{listOf("فیلم امروز","جست‌وجوی فیلم","موسیقی").forEach{FilterChip(selected=tab==it,onClick={tab=it},label={Text(it)})}}
  if(tab=="فیلم امروز"){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
    item{FCard{Text("انتخاب امروز",color=FG,fontWeight=FontWeight.Bold);Text(today.title,color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(today.year.toString()+" • "+today.genre+" • "+today.duration+" دقیقه",color=FG);Text(today.summary,color=Color.White);Text("چرا پیشنهادش می‌کنم؟",color=FG,fontWeight=FontWeight.SemiBold);Text(today.reason,color=FM);Button(onClick={onAskAi("درباره فیلم "+today.title+" معرفی کامل بدون اسپویل و تحلیل سبک کارگردانی بنویس.")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("تحلیل بیشتر با Naeem AI")}}}
    item{Text("بر اساس حال‌وهوا",color=FG,fontWeight=FontWeight.Bold);Row{listOf("همه","آرام","پرکشش","علمی‌تخیلی","درام").forEach{FilterChip(selected=mood==it,onClick={mood=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}}
    items(films.filter{mood=="همه"||it.genre.contains(mood)||(mood=="آرام"&&it.duration>=90)||(mood=="پرکشش"&&it.genre.contains("ماجراجویی"))}){f->FCard{Text(f.title,color=Color.White,fontWeight=FontWeight.Bold);Text(f.year.toString()+" • "+f.genre,color=FG);Text(f.summary,color=FM,style=MaterialTheme.typography.bodySmall);TextButton(onClick={onAskAi("معرفی و نقد بدون اسپویل فیلم "+f.title+" را بنویس.")}){Text("معرفی و نقد بیشتر",color=FG)}}}
   }
  }else if(tab=="جست‌وجوی فیلم"){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
    item{FInput("نام فیلم یا ژانر",query,{query=it},Modifier.fillMaxWidth());Button(onClick={onAskAi("چند فیلم خوب دربارهٔ "+query+" معرفی کن و خلاصه و دلیل انتخابشان را بگو.")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("پیشنهاد با هوش مصنوعی")}}
    items(films.filter{query.isBlank()||it.title.contains(query,true)||it.genre.contains(query,true)}){f->FCard{Text(f.title,color=Color.White,fontWeight=FontWeight.Bold);Text(f.year.toString()+" • "+f.genre,color=FG);Text(f.summary,color=FM,style=MaterialTheme.typography.bodySmall)}}
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
