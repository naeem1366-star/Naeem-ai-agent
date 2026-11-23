package com.naeem.ai

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SportLog(val id:String,val title:String,val category:String,val date:Long,val minutes:Int,val distance:Double,val sets:Int,val reps:Int,val weight:Double,val notes:String)
class SportStore(context:Context){
 private val prefs=context.getSharedPreferences("naeem_sports_v1",Context.MODE_PRIVATE)
 fun load():List<SportLog> = runCatching{val a=JSONArray(prefs.getString("logs","[]"));(0 until a.length()).mapNotNull{i->a.optJSONObject(i)?.let{o->SportLog(o.optString("id"),o.optString("title"),o.optString("category"),o.optLong("date"),o.optInt("minutes"),o.optDouble("distance"),o.optInt("sets"),o.optInt("reps"),o.optDouble("weight"),o.optString("notes"))}}}.getOrDefault(emptyList())
 fun save(logs:List<SportLog>){val a=JSONArray();logs.take(500).forEach{a.put(JSONObject().put("id",it.id).put("title",it.title).put("category",it.category).put("date",it.date).put("minutes",it.minutes).put("distance",it.distance).put("sets",it.sets).put("reps",it.reps).put("weight",it.weight).put("notes",it.notes))};prefs.edit().putString("logs",a.toString()).apply()}
}
@Composable
fun SportsDashboard(onClose:()->Unit){
 val context=LocalContext.current;val store=remember{SportStore(context)}
 var logs by remember{mutableStateOf(store.load())};var tab by remember{mutableStateOf("تمرین")}
 var title by remember{mutableStateOf("")};var category by remember{mutableStateOf("قدرتی")}
 var minutes by remember{mutableStateOf("45")};var sets by remember{mutableStateOf("3")};var reps by remember{mutableStateOf("10")}
 var weight by remember{mutableStateOf("0")};var distance by remember{mutableStateOf("0")};var notes by remember{mutableStateOf("")}
 var goal by remember{mutableStateOf("افزایش قدرت")};var selectedPlan by remember{mutableStateOf("قدرت پایه")}
 val bg=Color(0xFF0A0C10);val panel=Color(0xFF1B1F25);val accent=Color(0xFFD4D9E0);val muted=Color(0xFFB5B9C8)
 val today=java.util.Calendar.getInstance().apply{set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
 val week=logs.filter{it.date>=today-6L*24*60*60*1000};val weekMinutes=week.sumOf{it.minutes}
 val volume=week.sumOf{it.sets.toLong()*it.reps.toLong()*(it.weight*100).toLong()/100L}
 val number=remember{java.text.NumberFormat.getNumberInstance(Locale("fa","IR"))}
 val plans=mapOf("قدرت پایه" to listOf("اسکوات","پرس سینه","زیربغل قایقی","پرس سرشانه","ددلیفت رومانیایی","پلانک"),"عضله‌سازی" to listOf("پرس سینه دمبل","لت سیم‌کش","اسکوات جام","پرس بالا سینه","جلو بازو","پشت بازو"),"چربی‌سوزی و آمادگی" to listOf("پیاده‌روی تند","دوچرخه ثابت","اسکوات وزن بدن","شنا سوئدی","طناب","حرکات مرکزی"),"انعطاف و ریکاوری" to listOf("کشش همسترینگ","باز کردن لگن","کشش سینه","حرکت گربه گاو","تنفس آرام","پیاده‌روی سبک"))
 fun saveLog(){if(title.isNotBlank()){val x=SportLog(UUID.randomUUID().toString(),title.trim(),category,System.currentTimeMillis(),minutes.toIntOrNull()?.coerceAtLeast(0)?:0,distance.toDoubleOrNull()?:0.0,sets.toIntOrNull()?.coerceAtLeast(0)?:0,reps.toIntOrNull()?.coerceAtLeast(0)?:0,weight.toDoubleOrNull()?.coerceAtLeast(0.0)?:0.0,notes.trim());logs=listOf(x)+logs;store.save(logs);title="";notes=""}}
 Column(Modifier.fillMaxSize().background(bg).mountainBackdrop(Color(0xFF91BFE8)).padding(horizontal=16.dp,vertical=12.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("NAEEM PERFORMANCE",color=accent,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Text("باشگاه تمرین من",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("تمرین، قدرت، استقامت و ریکاوری؛ همه در یک‌جا",color=muted,style=MaterialTheme.typography.bodySmall)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=accent)}}
  Spacer(Modifier.height(12.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("تمرین","برنامه‌ها","پیشرفت","تاریخچه").forEach{FilterChip(selected=tab==it,onClick={tab=it},label={Text(it)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=accent,selectedLabelColor=bg,containerColor=panel,labelColor=Color.White))}}
  Spacer(Modifier.height(10.dp))
  when(tab){
   "تمرین"->LazyColumn(verticalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(bottom=24.dp)){
    item{Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Text("خلاصهٔ هفت روز اخیر",color=accent,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){SportMetric("جلسه‌ها",week.size.toString(),Modifier.weight(1f),accent,panel);SportMetric("دقیقه تمرین",number.format(weekMinutes),Modifier.weight(1f),accent,panel);SportMetric("حجم وزنه",number.format(volume),Modifier.weight(1f),accent,panel)};Text("هدف فعلی: "+goal,color=muted,style=MaterialTheme.typography.labelSmall)}}}
    item{Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Text("ثبت جلسهٔ تمرین",color=accent,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
     Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("قدرتی","هوازی","انعطاف","ورزش تیمی","طبیعت‌گردی").forEach{FilterChip(selected=category==it,onClick={category=it},label={Text(it,style=MaterialTheme.typography.labelSmall)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=accent,selectedLabelColor=bg,containerColor=bg,labelColor=Color.White))}}
     OutlinedTextField(title,{title=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text(if(category=="قدرتی")"نام حرکت (مثلاً اسکوات)" else "نوع فعالیت")})
     if(category=="قدرتی"){Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){SportInput("ست",sets,{sets=it.filter(Char::isDigit)},Modifier.weight(1f));SportInput("تکرار",reps,{reps=it.filter(Char::isDigit)},Modifier.weight(1f));SportInput("وزنه kg",weight,{weight=it.filter{c->c.isDigit()||c=='.'}},Modifier.weight(1f))};Text("حجم این حرکت: "+number.format((sets.toIntOrNull()?:0)*(reps.toIntOrNull()?:0)*(weight.toDoubleOrNull()?:0.0))+" کیلوگرم",color=muted,style=MaterialTheme.typography.labelSmall)}
     else{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){SportInput("مدت دقیقه",minutes,{minutes=it.filter(Char::isDigit)},Modifier.weight(1f));SportInput("مسافت km",distance,{distance=it.filter{c->c.isDigit()||c=='.'}},Modifier.weight(1f))}}
     if(category=="قدرتی")SportInput("مدت تمرین به دقیقه",minutes,{minutes=it.filter(Char::isDigit)},Modifier.fillMaxWidth())
     OutlinedTextField(notes,{notes=it},modifier=Modifier.fillMaxWidth(),label={Text("یادداشت، شدت تمرین یا حس بدنی (اختیاری)")},minLines=2)
     Button(onClick={saveLog()},enabled=title.isNotBlank(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=bg)){Text("ذخیرهٔ تمرین",fontWeight=FontWeight.Bold)}
    }}}
    item{Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("میانبر فعالیت‌ها",color=accent,fontWeight=FontWeight.Bold);listOf("پیاده‌روی","دوچرخه‌سواری","دویدن","شنا","کوهنوردی","یوگا").forEach{activity->TextButton(onClick={category=if(activity=="یوگا")"انعطاف" else if(activity=="کوهنوردی")"طبیعت‌گردی" else "هوازی";title=activity}){Text(activity,color=Color.White)}}}}}
   }
   "برنامه‌ها"->LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
    item{Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("برنامهٔ تمرینی پیشنهادی",color=accent,fontWeight=FontWeight.Bold);listOf("قدرت پایه","عضله‌سازی","چربی‌سوزی و آمادگی","انعطاف و ریکاوری").forEach{p->FilterChip(selected=selectedPlan==p,onClick={selectedPlan=p},label={Text(p)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=accent,selectedLabelColor=bg,containerColor=bg,labelColor=Color.White))};Text("هدف: "+goal,color=muted);Row{listOf("افزایش قدرت","عضله‌سازی","سلامت عمومی","استقامت").forEach{FilterChip(selected=goal==it,onClick={goal=it},label={Text(it,style=MaterialTheme.typography.labelSmall)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=accent,selectedLabelColor=bg,containerColor=bg,labelColor=Color.White))}}}}}
    item{Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(selectedPlan+" • حرکات پیشنهادی",color=accent,fontWeight=FontWeight.Bold);plans[selectedPlan].orEmpty().forEachIndexed{i,exercise->Row(verticalAlignment=Alignment.CenterVertically){Text((i+1).toString().padStart(2,'0'),color=accent,fontWeight=FontWeight.Bold);Spacer(Modifier.width(10.dp));Text(exercise,color=Color.White,modifier=Modifier.weight(1f));Text(if(selectedPlan=="انعطاف و ریکاوری")"۳۰ ثانیه" else "۳ × ۱۰",color=muted,style=MaterialTheme.typography.labelSmall);TextButton(onClick={title=exercise;category=if(selectedPlan=="انعطاف و ریکاوری")"انعطاف" else "قدرتی";tab="تمرین"}){Text("ثبت")}}};Text("این‌ها الگوهای عمومی‌اند؛ وزنه و شدت را متناسب با آمادگی خودت انتخاب کن.",color=muted,style=MaterialTheme.typography.labelSmall)}}}
   }
   "پیشرفت"->LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
    item{Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Text("داشبورد پیشرفت",color=accent,fontWeight=FontWeight.Bold);SportMetric("کل جلسات ثبت‌شده",logs.size.toString(),Modifier.fillMaxWidth(),accent,panel);SportMetric("زمان تمرین این هفته",weekMinutes.toString()+" دقیقه",Modifier.fillMaxWidth(),accent,panel);SportMetric("حجم وزنه این هفته",number.format(volume)+" kg",Modifier.fillMaxWidth(),accent,panel);SportMetric("مسافت هوازی",String.format(Locale.US,"%.1f km",week.sumOf{it.distance}),Modifier.fillMaxWidth(),accent,panel)}}}
    item{Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("رکوردهای شخصی ثبت‌شده",color=accent,fontWeight=FontWeight.Bold);val records=logs.filter{it.category=="قدرتی"&&it.weight>0}.groupBy{it.title}.mapValues{(_,items)->items.maxOf{it.weight}};if(records.isEmpty())Text("با ثبت تمرین‌های قدرتی، رکورد هر حرکت اینجا ساخته می‌شود.",color=muted);records.forEach{(name,best)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(name,color=Color.White);Text(number.format(best)+" kg",color=accent,fontWeight=FontWeight.Bold)}}}}}
   }
   else->LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)){
    if(logs.isEmpty())item{Text("هنوز تمرینی ثبت نشده است.",color=muted)}
    items(logs,key={it.id}){log->Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(17.dp)){Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(log.title,color=Color.White,fontWeight=FontWeight.Bold);Text(log.category+" • "+SimpleDateFormat("yyyy/MM/dd  HH:mm",Locale.getDefault()).format(Date(log.date)),color=muted,style=MaterialTheme.typography.labelSmall);Text(if(log.category=="قدرتی")log.sets.toString()+" ست × "+log.reps+" تکرار • "+log.weight+" kg" else log.minutes.toString()+" دقیقه • "+log.distance+" km",color=accent,style=MaterialTheme.typography.bodySmall);if(log.notes.isNotBlank())Text(log.notes,color=muted,style=MaterialTheme.typography.bodySmall)};IconButton(onClick={logs=logs.filterNot{it.id==log.id};store.save(logs)}){Icon(Icons.Default.Delete,null,tint=Color(0xFFFF9A91))}}}}
   }
  }
 }
}
@Composable private fun SportInput(label:String,value:String,onValue:(String)->Unit,modifier:Modifier){OutlinedTextField(value,onValue,modifier=modifier,singleLine=true,label={Text(label,style=MaterialTheme.typography.labelSmall)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))}
@Composable private fun SportMetric(title:String,value:String,modifier:Modifier,accent:Color,panel:Color){Card(modifier,colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(15.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(title,color=Color(0xFFB5B9C8),style=MaterialTheme.typography.labelSmall);Text(value,color=accent,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}}}
