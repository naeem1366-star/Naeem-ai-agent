package com.naeem.ai

import android.app.*
import android.content.*
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.util.*

private val PB=SupraGlass.Night; private val PC=SupraGlass.Glass; private val PT=SupraGlass.Silver; private val PM=SupraGlass.Muted; private val PA=SupraGlass.Ice
private data class RItem(val id:Int,val title:String,val detail:String,val at:Long,val daily:Boolean)
private class RStore(c:Context) {
 private val p=c.getSharedPreferences("naeem_reminders",0)
 fun get(k:String):List<RItem> = runCatching { val a=org.json.JSONArray(p.getString(k,"[]")); (0 until a.length()).mapNotNull { i->a.optJSONObject(i)?.let { RItem(it.optInt("id"),it.optString("title"),it.optString("detail"),it.optLong("at"),it.optBoolean("daily")) } } }.getOrDefault(emptyList())
 fun put(k:String,v:List<RItem>) { val a=org.json.JSONArray();v.forEach { a.put(org.json.JSONObject().put("id",it.id).put("title",it.title).put("detail",it.detail).put("at",it.at).put("daily",it.daily)) };p.edit().putString(k,a.toString()).apply() }
}
private fun setReminder(c:Context,r:RItem) {
 val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
 val i=Intent(c,NReminderReceiver::class.java).putExtra("id",r.id).putExtra("title",r.title).putExtra("detail",r.detail)
 val pi=PendingIntent.getBroadcast(c,r.id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 if(r.daily) am.setInexactRepeating(AlarmManager.RTC_WAKEUP,r.at,AlarmManager.INTERVAL_DAY,pi)
 else if(r.at>System.currentTimeMillis()) { if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.at,pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.at,pi) }
}
private fun cancelReminder(c:Context,id:Int) { val pi=PendingIntent.getBroadcast(c,id,Intent(c,NReminderReceiver::class.java),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE); if(pi!=null)(c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi) }
class NReminderReceiver:BroadcastReceiver() {
 override fun onReceive(c:Context,i:Intent) {
  val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(NotificationChannel("naeem_reminders","یادآوری‌های Naeem AI",NotificationManager.IMPORTANCE_HIGH))
  val n=NotificationCompat.Builder(c,"naeem_reminders").setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(i.getStringExtra("title")?:"یادآوری").setContentText(i.getStringExtra("detail")?:"زمان انجام کار رسیده است").setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
  if(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(c,android.Manifest.permission.POST_NOTIFICATIONS)==0) runCatching { NotificationManagerCompat.from(c).notify(i.getIntExtra("id",1),n) }
 }
}
@Composable private fun RHeader(t:String,s:String,close:()->Unit) { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(t,color=PT,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(s,color=PM,style=MaterialTheme.typography.bodySmall)};IconButton(onClick=close){Icon(Icons.Default.Close,null,tint=PA)} } }
@Composable private fun RField(label:String,v:String,change:(String)->Unit,m:Modifier=Modifier) { OutlinedTextField(v,change,m,label={Text(label)},singleLine=true,shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=PA,unfocusedBorderColor=Color(0xFF46515F),focusedContainerColor=PC,unfocusedContainerColor=PC)) }
private fun reminderTime(d:String,t:String):Long?=runCatching{SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).apply{isLenient=false}.parse(d+" "+t)!!.time}.getOrNull()
@Composable private fun AskNotificationPermission() {
 val c=LocalContext.current
 val launcher=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){ }
 LaunchedEffect(Unit){if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(c,android.Manifest.permission.POST_NOTIFICATIONS)!=0)launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)}
}
@Composable fun WeeklyPlannerDashboard(onClose:()->Unit) {
 val c=LocalContext.current;val st=remember{RStore(c)};var tasks by remember{mutableStateOf(st.get("tasks"))}
 var title by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var date by remember{mutableStateOf(SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(System.currentTimeMillis()+86400000L)))};var time by remember{mutableStateOf("09:00")};var msg by remember{mutableStateOf("")}
 AskNotificationPermission()
 Column(Modifier.fillMaxSize().background(Color.Transparent).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  RHeader("نوت‌بوک","ثبت کارها و یادآوری در زمان دلخواه",onClose)
  Card(colors=CardDefaults.cardColors(containerColor=PC),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   RField("عنوان کار",title,{title=it},Modifier.fillMaxWidth());RField("توضیحات",note,{note=it},Modifier.fillMaxWidth())
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){RField("تاریخ YYYY-MM-DD",date,{date=it},Modifier.weight(1.2f));RField("ساعت HH:mm",time,{time=it},Modifier.weight(.8f))}
   Button(onClick={val at=reminderTime(date,time);if(title.isBlank()||at==null||at<=System.currentTimeMillis())msg="عنوان، تاریخ و ساعت معتبرِ آینده وارد کن." else {val r=RItem((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),title.trim(),note.ifBlank{"زمان انجام کار رسیده است."},at,false);tasks=(tasks+r).sortedBy{it.at};st.put("tasks",tasks);setReminder(c,r);title="";note="";msg="کار ثبت شد و اعلان تنظیم شد."}},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF456B58))){Icon(Icons.Default.Add,null);Text("ثبت کار و یادآوری")}
   if(msg.isNotBlank())Text(msg,color=PM)
  }}
  Text("کارهای ثبت‌شده",color=PT,fontWeight=FontWeight.Bold)
  if(tasks.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("هنوز کاری ثبت نکردی.",color=PM)} else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(tasks,key={it.id}){r->Card(colors=CardDefaults.cardColors(containerColor=PC),shape=RoundedCornerShape(15.dp)){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(r.title,color=PT,fontWeight=FontWeight.SemiBold);Text(SimpleDateFormat("EEE dd MMM · HH:mm",Locale("fa")).format(Date(r.at))+" · "+r.detail,color=PM,style=MaterialTheme.typography.bodySmall)};IconButton(onClick={cancelReminder(c,r.id);tasks=tasks.filterNot{it.id==r.id};st.put("tasks",tasks)}){Icon(Icons.Default.DeleteOutline,null,tint=Color(0xFFE59A9A))}}}}}
  Text("یادآوری کارها یک‌بار در تاریخ و ساعت انتخاب‌شده ارسال می‌شود.",color=PM,style=MaterialTheme.typography.labelSmall)
 }
}
@Composable fun MedicationReminderDashboard(onClose:()->Unit) {
 val c=LocalContext.current;val st=remember{RStore(c)};var meds by remember{mutableStateOf(st.get("meds"))};var name by remember{mutableStateOf("")};var dose by remember{mutableStateOf("")};var time by remember{mutableStateOf("09:00")};var msg by remember{mutableStateOf("")}
 AskNotificationPermission()
 Column(Modifier.fillMaxSize().background(Color.Transparent).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  RHeader("یادآور دارو","ثبت دارو و اعلان تکرارشونده روزانه",onClose)
  Card(colors=CardDefaults.cardColors(containerColor=PC),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   RField("نام دارو",name,{name=it},Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){RField("مقدار / دوز",dose,{dose=it},Modifier.weight(1f));RField("ساعت HH:mm",time,{time=it},Modifier.weight(1f))}
   Button(onClick={val p=time.split(":");val h=p.getOrNull(0)?.toIntOrNull();val m=p.getOrNull(1)?.toIntOrNull();if(name.isBlank()||h==null||m==null||h !in 0..23||m !in 0..59)msg="نام دارو و ساعت معتبر وارد کن." else {val cal=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,h);set(Calendar.MINUTE,m);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);if(timeInMillis<=System.currentTimeMillis())add(Calendar.DAY_OF_YEAR,1)};val r=RItem((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),name.trim(),(dose+" · زمان مصرف دارو رسیده است").trim(' ','·'),cal.timeInMillis,true);meds=meds+r;st.put("meds",meds);setReminder(c,r);name="";dose="";msg="یادآور روزانه ثبت شد."}},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF456B58))){Icon(Icons.Default.Add,null);Text("ثبت یادآور روزانه")}
   if(msg.isNotBlank())Text(msg,color=PM)
  }}
  Text("داروهای ثبت‌شده",color=PT,fontWeight=FontWeight.Bold)
  if(meds.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("هنوز یادآور دارویی نداری.",color=PM)} else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(meds,key={it.id}){r->Card(colors=CardDefaults.cardColors(containerColor=PC),shape=RoundedCornerShape(15.dp)){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(r.title,color=PT,fontWeight=FontWeight.SemiBold);Text(r.detail+" · هر روز "+SimpleDateFormat("HH:mm",Locale.US).format(Date(r.at)),color=PM,style=MaterialTheme.typography.bodySmall)};IconButton(onClick={cancelReminder(c,r.id);meds=meds.filterNot{it.id==r.id};st.put("meds",meds)}){Icon(Icons.Default.DeleteOutline,null,tint=Color(0xFFE59A9A))}}}}}
 }
}
private data class NItem(val title:String,val source:String,val date:String,val url:String,val summary:String)
@Composable fun NewsDashboard(onClose:()->Unit) {
 var items by remember{mutableStateOf<List<NItem>>(emptyList())};var loading by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope();val c=LocalContext.current
 fun refresh(){scope.launch{loading=true;error="";try{items=withContext(Dispatchers.IO){val req=Request.Builder().url("https://news.google.com/rss?hl=fa&gl=IR&ceid=IR:fa").header("User-Agent","NaeemAI/2.0").build();OkHttpClient().newCall(req).execute().use{res->if(!res.isSuccessful)throw Exception("خطای دریافت خبر: "+res.code);val xml=res.body?.string()?:throw Exception("پاسخ منبع خالی است");Jsoup.parse(xml,"",Parser.xmlParser()).select("item").take(35).map{n->NItem(n.selectFirst("title")?.text().orEmpty(),n.selectFirst("source")?.text().orEmpty(),n.selectFirst("pubDate")?.text().orEmpty(),n.selectFirst("link")?.text().orEmpty(),Jsoup.parse(n.selectFirst("description")?.text().orEmpty()).text())}}};if(items.isEmpty())error="فعلاً خبری دریافت نشد."}catch(e:Exception){error=e.message?:"اتصال به منبع خبر برقرار نشد."}finally{loading=false}}}
 LaunchedEffect(Unit){refresh()}
 Column(Modifier.fillMaxSize().background(Color.Transparent).padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  RHeader("اخبار مهم","خوراک خبری فارسی ایران و جهان",onClose)
  Row(verticalAlignment=Alignment.CenterVertically){Text(if(items.isEmpty())"آخرین تیترها" else items.size.toString()+" خبر دریافت شد",color=PM,modifier=Modifier.weight(1f));TextButton(onClick={refresh()},enabled=!loading){Icon(Icons.Default.Refresh,null);Text("به‌روزرسانی")}}
  if(loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=SupraGlass.Ice)
  if(error.isNotBlank())Text(error,color=Color(0xFFE6A1A1))
  if(items.isEmpty()&&!loading)Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text("برای دریافت خبر دوباره تلاش کن.",color=PM)}else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(items){n->Card(Modifier.fillMaxWidth().clickable{runCatching{c.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(n.url))) }},colors=CardDefaults.cardColors(containerColor=PC),shape=RoundedCornerShape(15.dp)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(n.title,color=PT,fontWeight=FontWeight.SemiBold);Text(n.source+" · "+n.date,color=SupraGlass.Ice,style=MaterialTheme.typography.labelSmall);if(n.summary.isNotBlank())Text(n.summary.take(220),color=PM,style=MaterialTheme.typography.bodySmall,maxLines=3);Text("باز کردن منبع ↗",color=SupraGlass.Ice,style=MaterialTheme.typography.labelSmall)}}}}
  Text("منبع: Google News RSS؛ برای متن کامل، خبر را باز کن.",color=PM,style=MaterialTheme.typography.labelSmall)
 }
}
