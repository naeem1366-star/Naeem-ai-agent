package com.naeem.ai

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private data class TripExpense(val id:String, val title:String, val amount:Long, val payer:String)
private data class LocalTrip(
    val id:String, val title:String, val destination:String, val dates:String,
    val members:List<String>, val expenses:List<TripExpense>
)
private class TravelStore(context:Context) {
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
                    expensesJson.optJSONObject(j)?.let { e -> TripExpense(e.optString("id"),e.optString("title"),e.optLong("amount"),e.optString("payer")) }
                }
            )
        }
    }.getOrDefault(emptyList())
    fun save(trips:List<LocalTrip>) {
        val a=JSONArray()
        trips.forEach { t ->
            val m=JSONArray(); t.members.forEach(m::put)
            val e=JSONArray(); t.expenses.forEach { x -> e.put(JSONObject().put("id",x.id).put("title",x.title).put("amount",x.amount).put("payer",x.payer)) }
            a.put(JSONObject().put("id",t.id).put("title",t.title).put("destination",t.destination).put("dates",t.dates).put("members",m).put("expenses",e))
        }
        prefs.edit().putString("trips",a.toString()).apply()
    }
}

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
    val selected=trips.firstOrNull { it.id==selectedId }
    val number= remember { NumberFormat.getNumberInstance(Locale("fa","IR")) }
    val bg=Color(0xFF0D1820); val panel=Color(0xFF172A36); val accent=Color(0xFF73D2D0); val muted=Color(0xFFA8BDC5)
    fun persist(next:List<LocalTrip>) { trips=next; store.save(next) }

    Column(Modifier.fillMaxSize().background(bg).padding(horizontal=18.dp, vertical=14.dp)) {
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
                                Text(number.format(trip.expenses.sumOf{it.amount})+" تومان",color=Color.White,fontWeight=FontWeight.SemiBold)
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
                    Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.PersonAdd,null,tint=accent); Spacer(Modifier.width(8.dp)); Text("هم‌سفرها",color=Color.White,fontWeight=FontWeight.Bold) }
                            selected.members.forEach { Text("•  "+it,color=muted) }
                            OutlinedTextField(memberName,{memberName=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("نام هم‌سفر جدید")})
                            Button(onClick={
                                val n=memberName.trim()
                                if(n.isNotBlank() && !selected.members.any{it.equals(n,true)}) persist(trips.map{if(it.id==selected.id)it.copy(members=it.members+n)else it})
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
                            Button(onClick={
                                val amount=expenseAmount.toLongOrNull()?:0L
                                if(expenseTitle.isNotBlank()&&amount>0) {
                                    val x=TripExpense(UUID.randomUUID().toString(),expenseTitle.trim(),amount,payer.ifBlank{selected.members.firstOrNull().orEmpty()})
                                    persist(trips.map{if(it.id==selected.id)it.copy(expenses=it.expenses+x)else it})
                                    expenseTitle="";expenseAmount="";payer=""
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
                            Column(Modifier.weight(1f)) { Text(expense.title,color=Color.White,fontWeight=FontWeight.SemiBold); Text("پرداخت توسط "+expense.payer,color=muted,style=MaterialTheme.typography.labelSmall) }
                            Text(number.format(expense.amount)+" تومان",color=accent,fontWeight=FontWeight.Bold)
                        }
                    }
                }
                item {
                    Text("سهم مساوی تقریبی هر نفر: "+number.format(if(selected.members.isEmpty())0 else selected.expenses.sumOf{it.amount}/selected.members.size)+" تومان",color=muted,style=MaterialTheme.typography.bodySmall)
                    Text("این نسخه هزینه‌ها را روی همین گوشی نگه می‌دارد؛ دعوت و همگام‌سازی آنلاین هنوز فعال نیست.",color=muted,style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
