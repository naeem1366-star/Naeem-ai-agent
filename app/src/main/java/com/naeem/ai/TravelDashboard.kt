package com.naeem.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

data class TripExpense(val id:String, val title:String, val amount:Long, val payer:String, val beneficiaries:List<String> = emptyList())
data class LocalTrip(
    val id:String, val title:String, val destination:String, val dates:String,
    val members:List<String>, val expenses:List<TripExpense>
)
class TravelStore(context:Context) {
    private val prefs=context.getSharedPreferences("naeem_travel_local",Context.MODE_PRIVATE)
    fun load():List<LocalTrip> = runCatching {
        val a=JSONArray(prefs.getString("trips","[]"))
        (0 until a.length()).mapNotNull { i ->
            val o=a.optJSONObject(i) ?: return@mapNotNull null
            val membersJson=o.optJSONArray("members") ?: JSONArray()
            val expensesJson=o.optJSONArray("expenses") ?: JSONArray()
            LocalTrip(
                o.optString("id"),o.optString("title"),o.optString("destination"),o.optString("dates"),
                (0 until membersJson.length()).map { membersJson.optString(it) },
                (0 until expensesJson.length()).mapNotNull { j ->
                    expensesJson.optJSONObject(j)?.let { e ->
                        val beneficiaryJson = e.optJSONArray("beneficiaries")
                        val beneficiaries = if (beneficiaryJson == null) (0 until membersJson.length()).map { membersJson.optString(it) }
                            else (0 until beneficiaryJson.length()).map { beneficiaryJson.optString(it) }
                        TripExpense(e.optString("id"),e.optString("title"),e.optLong("amount"),e.optString("payer"),beneficiaries)
                    }
                }
            )
        }
    }.getOrDefault(emptyList())
    fun save(trips:List<LocalTrip>) {
        val a=JSONArray()
        trips.forEach { t ->
            val m=JSONArray(); t.members.forEach(m::put)
            val e=JSONArray(); t.expenses.forEach { x ->
                val beneficiaries = JSONArray(); x.beneficiaries.forEach(beneficiaries::put)
                e.put(JSONObject().put("id",x.id).put("title",x.title).put("amount",x.amount).put("payer",x.payer).put("beneficiaries",beneficiaries))
            }
            a.put(JSONObject().put("id",t.id).put("title",t.title).put("destination",t.destination).put("dates",t.dates).put("members",m).put("expenses",e))
        }
        prefs.edit().putString("trips",a.toString()).apply()
    }
}

private fun fetchTripWeather(place:String):String {
    val client=OkHttpClient()
    val q=URLEncoder.encode(place,"UTF-8")
    val geoRequest=Request.Builder().url("https://geocoding-api.open-meteo.com/v1/search?name="+q+"&count=1&language=fa&format=json").build()
    val geo=client.newCall(geoRequest).execute().use { response ->
        if(!response.isSuccessful) throw IllegalStateException("سرویس مکان‌یابی پاسخ نداد.")
        JSONObject(response.body?.string().orEmpty())
    }
    val result=geo.optJSONArray("results")?.optJSONObject(0) ?: throw IllegalArgumentException("مقصد پیدا نشد؛ نام شهر یا منطقه را دقیق‌تر وارد کن.")
    val lat=result.optDouble("latitude");val lon=result.optDouble("longitude")
    val placeName=result.optString("name",place);val country=result.optString("country","")
    val url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,wind_speed_10m,weather_code"+
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max&timezone=auto&forecast_days=3"
    val forecast=client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if(!response.isSuccessful) throw IllegalStateException("دریافت پیش‌بینی هوا ناموفق بود.")
        JSONObject(response.body?.string().orEmpty())
    }
    val current=forecast.getJSONObject("current")
    val daily=forecast.getJSONObject("daily")
    fun condition(code:Int)=when(code){
        0->"آسمان صاف";1->"عمدتاً صاف";2->"نیمه‌ابری";3->"ابری"
        45,48->"مه‌آلود";51,53,55->"نم‌نم باران";56,57->"باران یخ‌زده"
        61,63,65->"بارانی";66,67->"باران یخ‌زده";71,73,75,77->"برف"
        80,81,82->"رگبار";85,86->"رگبار برف";95,96,99->"رعدوبرق";else->"وضعیت متغیر"
    }
    val lines=StringBuilder()
    lines.append("آب‌وهوای ").append(placeName).append(if(country.isNotBlank())"، "+country else "").append("\n")
    lines.append("اکنون: ").append(current.optDouble("temperature_2m").toInt()).append("° • حس‌شده ").append(current.optDouble("apparent_temperature").toInt()).append("° • ").append(condition(current.optInt("weather_code"))).append("\n")
    lines.append("رطوبت ").append(current.optInt("relative_humidity_2m")).append("% • باد ").append(current.optInt("wind_speed_10m")).append(" km/h • بارش ").append(current.optDouble("precipitation")).append(" mm\n")
    val dates=daily.getJSONArray("time");val codes=daily.getJSONArray("weather_code");val highs=daily.getJSONArray("temperature_2m_max");val lows=daily.getJSONArray("temperature_2m_min");val rain=daily.getJSONArray("precipitation_probability_max")
    for(i in 0 until minOf(3,dates.length())){
        lines.append(dates.optString(i)).append(": ").append(condition(codes.optInt(i))).append("، ").append(lows.optDouble(i).toInt()).append(" تا ").append(highs.optDouble(i).toInt()).append("°، احتمال بارش ").append(rain.optInt(i)).append("%\n")
    }
    lines.append("منبع: Open-Meteo • پیش‌بینی مقصد است، نه کل مسیر.")
    return lines.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelDashboard(onClose:()->Unit) {
    val context= LocalContext.current
    val store= remember { TravelStore(context) }
    var trips by remember { mutableStateOf(store.load()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var tripName by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var dates by remember { mutableStateOf("") }
    var memberName by remember { mutableStateOf("") }
    var expenseTitle by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }
    var payer by remember { mutableStateOf("") }
    var expenseBeneficiaries by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteTarget by remember { mutableStateOf<LocalTrip?>(null) }
    var weatherText by remember { mutableStateOf("") }
    var weatherError by remember { mutableStateOf("") }
    var weatherLoading by remember { mutableStateOf(false) }
    val scope= rememberCoroutineScope()
    val selected=trips.firstOrNull { it.id==selectedId }
    LaunchedEffect(selectedId) { expenseBeneficiaries = selected?.members?.toSet().orEmpty() }
    val number= remember { NumberFormat.getNumberInstance(Locale("fa","IR")) }
    val bg=Color(0xFF0A0C10); val panel=Color(0xFF1B1F25); val accent=Color(0xFFD4D9E0); val muted=Color(0xFF9AA3AE)
    fun persist(next:List<LocalTrip>) { trips=next; store.save(next) }

    Column(Modifier.fillMaxSize().background(bg.copy(alpha = 0.84f)).padding(horizontal=18.dp, vertical=14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            if(selected!=null) IconButton(onClick={selectedId=null}) { Icon(Icons.Default.ArrowBack,null,tint=accent) }
            Column(Modifier.weight(1f)) {
                Text("TRAVEL SPACE",color=accent,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Text(if(selected==null) "سفرهای من" else selected.title,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text(if(selected==null) "برنامه‌ریزی و مدیریت هزینه‌های سفر" else selected.destination+"  •  "+selected.dates,color=muted,style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick=onClose) { Icon(Icons.Default.Close,null,tint=accent) }
        }
        Spacer(Modifier.height(14.dp))
        if(selected==null) {
            Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Luggage,null,tint=accent,modifier=Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column { Text("سفر تازه بساز",color=Color.White,fontWeight=FontWeight.Bold); Text("اطلاعات اولیه را وارد کن؛ بقیه را بعداً تکمیل می‌کنی.",color=muted,style=MaterialTheme.typography.bodySmall) }
                    }
                    OutlinedTextField(tripName,{tripName=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("عنوان سفر")})
                    OutlinedTextField(destination,{destination=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("مقصد")})
                    OutlinedTextField(dates,{dates=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("تاریخ سفر (اختیاری)")})
                    Button(onClick={
                        if(tripName.isNotBlank() && destination.isNotBlank()) {
                            val t=LocalTrip(UUID.randomUUID().toString(),tripName.trim(),destination.trim(),dates.trim().ifBlank{"تاریخ ثبت نشده"},listOf("من"),emptyList())
                            persist(listOf(t)+trips); selectedId=t.id; tripName=""; destination=""; dates=""
                        }
                    },enabled=tripName.isNotBlank()&&destination.isNotBlank(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=bg)) { Text("ایجاد سفر",fontWeight=FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("سفرهای ثبت‌شده  •  "+trips.size,color=accent,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if(trips.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) { Text("هنوز سفری ثبت نکرده‌ای.",color=muted) }
            } else {
                LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                    items(trips,key={it.id}) { trip ->
                        Card(onClick={selectedId=trip.id},colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(20.dp)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(trip.title,color=Color.White,fontWeight=FontWeight.Bold)
                                    Text(trip.destination+"  •  "+trip.dates,color=muted,style=MaterialTheme.typography.bodySmall)
                                    Text(trip.members.size.toString()+" نفر  •  "+trip.expenses.size.toString()+" هزینه",color=accent,style=MaterialTheme.typography.labelSmall)
                                }
                                Column(horizontalAlignment=Alignment.End, verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(number.format(trip.expenses.sumOf{it.amount})+" تومان",color=Color.White,fontWeight=FontWeight.SemiBold)
                                    IconButton(onClick={ deleteTarget=trip }, modifier=Modifier.size(38.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription="حذف سفر", tint=Color(0xFFFF8D86))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=26.dp)) {
                item {
                    Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Text("خلاصه هزینه‌ها",color=accent,fontWeight=FontWeight.Bold)
                            Text(number.format(selected.expenses.sumOf{it.amount})+" تومان",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black)
                            Text(selected.expenses.size.toString()+" هزینه ثبت‌شده  •  "+selected.members.size.toString()+" هم‌سفر",color=muted,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF252B34)),shape=RoundedCornerShape(22.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("☁️", fontSize = 20.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("آب‌وهوا و شرایط مسیر",color=Color.White,fontWeight=FontWeight.Bold)
                            }
                            Text("پیش‌بینی مقصد برای ۳ روز آینده؛ برای اطلاعات جاده و ترافیک، مسیر را در نقشه باز کن.",color=muted,style=MaterialTheme.typography.bodySmall)
                            Button(onClick={
                                weatherLoading=true;weatherError=""
                                scope.launch {
                                    try { weatherText=withContext(Dispatchers.IO){fetchTripWeather(selected.destination)} }
                                    catch(e:Exception){weatherError=e.message ?: "دریافت اطلاعات هوا ممکن نشد."}
                                    weatherLoading=false
                                }
                            },enabled=!weatherLoading,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=bg)) {
                                Text("🌦️");Spacer(Modifier.width(7.dp));Text(if(weatherLoading)"در حال دریافت..." else "دریافت وضعیت هوا و پیش‌بینی")
                            }
                            if(weatherText.isNotBlank()) Text(weatherText,color=Color.White,style=MaterialTheme.typography.bodySmall)
                            if(weatherError.isNotBlank()) Text(weatherError,color=Color(0xFFBFC6D0),style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick={
                                val destinationQuery=URLEncoder.encode(selected.destination,"UTF-8")
                                val url="https://www.google.com/maps/dir/?api=1&destination="+destinationQuery+"&travelmode=driving&dir_action=navigate"
                                runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
                            },modifier=Modifier.fillMaxWidth()) {
                                Text("🧭");Spacer(Modifier.width(7.dp));Text("مسیریابی رانندگی و بررسی ترافیک")
                            }
                            Text("ترافیک زنده، محدودیت جاده و مسیرهای جایگزین به پوشش و داده‌های نقشه وابسته‌اند؛ برنامه وضعیت بسته‌بودن جاده را مستقلاً تأیید نمی‌کند.",color=muted,style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item {
                    Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.PersonAdd,null,tint=accent); Spacer(Modifier.width(8.dp)); Text("هم‌سفرها",color=Color.White,fontWeight=FontWeight.Bold) }
                            selected.members.forEach { Text("•  "+it,color=muted) }
                            OutlinedTextField(memberName,{memberName=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("نام هم‌سفر جدید")})
                            Button(onClick={
                                val n=memberName.trim()
                                if(n.isNotBlank() && !selected.members.any{it.equals(n,true)}) {
                                    persist(trips.map{if(it.id==selected.id)it.copy(members=it.members+n)else it})
                                    expenseBeneficiaries = expenseBeneficiaries + n
                                }
                                memberName=""
                            },enabled=memberName.isNotBlank(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=bg)) { Text("افزودن هم‌سفر") }
                        }
                    }
                }
                item {
                    Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.ReceiptLong,null,tint=accent); Spacer(Modifier.width(8.dp)); Text("ثبت هزینه",color=Color.White,fontWeight=FontWeight.Bold) }
                            OutlinedTextField(expenseTitle,{expenseTitle=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("عنوان هزینه")})
                            OutlinedTextField(expenseAmount,{expenseAmount=it.filter(Char::isDigit)},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("مبلغ به تومان")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}) {
                                OutlinedTextField(value=payer.ifBlank{selected.members.firstOrNull().orEmpty()},onValueChange={},readOnly=true,modifier=Modifier.menuAnchor().fillMaxWidth(),label={Text("پرداخت‌کننده")})
                                ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                                    selected.members.forEach { name -> DropdownMenuItem(text={Text(name)},onClick={payer=name;expanded=false}) }
                                }
                            }
                            Text("این هزینه بین چه کسانی تقسیم شود؟", color=muted, style=MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement=Arrangement.spacedBy(5.dp), modifier=Modifier.fillMaxWidth()) {
                                selected.members.forEach { name ->
                                    FilterChip(
                                        selected = expenseBeneficiaries.contains(name),
                                        onClick = {
                                            expenseBeneficiaries = if (expenseBeneficiaries.contains(name)) expenseBeneficiaries - name else expenseBeneficiaries + name
                                        },
                                        label = { Text(name, style=MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Button(onClick={
                                val amount=expenseAmount.toLongOrNull()?:0L
                                if(expenseTitle.isNotBlank()&&amount>0) {
                                    val beneficiaries = expenseBeneficiaries.filter { it in selected.members }.ifEmpty { selected.members }
                                    val x=TripExpense(UUID.randomUUID().toString(),expenseTitle.trim(),amount,payer.ifBlank{selected.members.firstOrNull().orEmpty()},beneficiaries)
                                    persist(trips.map{if(it.id==selected.id)it.copy(expenses=it.expenses+x)else it})
                                    expenseTitle="";expenseAmount="";payer="";expenseBeneficiaries=selected.members.toSet()
                                }
                            },enabled=expenseTitle.isNotBlank()&&(expenseAmount.toLongOrNull()?:0L)>0,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=bg)) { Text("ثبت هزینه") }
                        }
                    }
                }
                item { Text("ریز هزینه‌ها",color=accent,fontWeight=FontWeight.Bold) }
                if(selected.expenses.isEmpty()) item { Text("هنوز هزینه‌ای ثبت نشده.",color=muted) }
                items(selected.expenses.reversed(),key={it.id}) { expense ->
                    Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(expense.title,color=Color.White,fontWeight=FontWeight.SemiBold)
                                Text("پرداخت توسط "+expense.payer+" • سهم: "+(expense.beneficiaries.ifEmpty { selected.members }.joinToString("، ")),color=muted,style=MaterialTheme.typography.labelSmall)
                            }
                            Text(number.format(expense.amount)+" تومان",color=accent,fontWeight=FontWeight.Bold)
                        }
                    }
                }
                item {
                    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF252B34)),shape=RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Text("تسویهٔ هوشمند سهم‌ها",color=accent,fontWeight=FontWeight.Bold)
                            Text("سهم هر نفر بر اساس افراد انتخاب‌شده برای هر هزینه محاسبه می‌شود؛ نه تقسیم مساوی کل سفر.",color=muted,style=MaterialTheme.typography.labelSmall)
                            val settlements = calculateTripSettlements(selected)
                            if(settlements.isEmpty()) Text("در حال حاضر کسی به دیگری بدهکار نیست.",color=Color.White)
                            else settlements.forEach { line -> Text(line,color=Color.White,fontWeight=FontWeight.SemiBold) }
                        }
                    }
                    Text("این نسخه هزینه‌ها را روی همین گوشی نگه می‌دارد؛ دعوت و همگام‌سازی آنلاین هنوز فعال نیست.",color=muted,style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    deleteTarget?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف سفر") },
            text = { Text("سفر «${trip.title}» و تمام هم‌سفرها و هزینه‌های ثبت‌شده آن حذف شود؟ این کار قابل بازگشت نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    persist(trips.filterNot { it.id == trip.id })
                    if (selectedId == trip.id) selectedId = null
                    deleteTarget = null
                }) { Text("حذف", color = Color(0xFFFF8D86)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("انصراف") }
            },
            containerColor = panel,
            titleContentColor = Color.White,
            textContentColor = muted
        )
    }
}


private fun calculateTripSettlements(trip: LocalTrip): List<String> {
    val members = trip.members.distinct()
    if (members.isEmpty()) return emptyList()
    val paid = members.associateWith { member -> trip.expenses.filter { it.payer == member }.sumOf { it.amount } }
    val owed = members.associateWith { 0L }.toMutableMap()
    trip.expenses.forEach { expense ->
        val beneficiaries = expense.beneficiaries.ifEmpty { members }.filter { it in members }.distinct()
        if (beneficiaries.isEmpty()) return@forEach
        val base = expense.amount / beneficiaries.size
        val remainder = expense.amount % beneficiaries.size
        beneficiaries.forEachIndexed { index, person ->
            owed[person] = (owed[person] ?: 0L) + base + if (index < remainder) 1L else 0L
        }
    }
    data class Balance(val person: String, var amount: Long)
    val debtors = members.map { Balance(it, ((owed[it] ?: 0L) - (paid[it] ?: 0L)).coerceAtLeast(0L)) }
        .filter { it.amount > 0L }.toMutableList()
    val creditors = members.map { Balance(it, ((paid[it] ?: 0L) - (owed[it] ?: 0L)).coerceAtLeast(0L)) }
        .filter { it.amount > 0L }.toMutableList()
    val number = NumberFormat.getNumberInstance(Locale("fa", "IR"))
    val result = mutableListOf<String>()
    var debtorIndex = 0
    var creditorIndex = 0
    while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
        val debtor = debtors[debtorIndex]
        val creditor = creditors[creditorIndex]
        val amount = minOf(debtor.amount, creditor.amount)
        if (amount > 0L) result.add(debtor.person + " → " + creditor.person + " : " + number.format(amount) + " تومان")
        debtor.amount -= amount
        creditor.amount -= amount
        if (debtor.amount == 0L) debtorIndex++
        if (creditor.amount == 0L) creditorIndex++
    }
    return result
}
