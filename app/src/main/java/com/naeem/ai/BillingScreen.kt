package com.naeem.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BillingScreen(accessToken:String,gatewayUrl:String,isEnglish:Boolean,onClose:()->Unit) {
    val client=remember(gatewayUrl) { BillingClient(gatewayUrl) }
    val scope= rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<BillingSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var topupAmount by remember { mutableStateOf("100000") }
    var receiptDrafts by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var workingOrder by remember { mutableStateOf("") }
    fun money(value:Long)=NumberFormat.getNumberInstance(Locale.US).format(value)+(if(isEnglish) " toman" else " تومان")
    fun refresh() {
        scope.launch {
            loading=true; error=""
            try { snapshot=client.load(accessToken) }
            catch(e:Exception) { error=e.message ?: if(isEnglish) "Could not load wallet." else "بارگذاری کیف پول ناموفق بود." }
            finally { loading=false }
        }
    }
    LaunchedEffect(accessToken,gatewayUrl) { refresh() }
    fun createOrder(purpose:String,amount:Long=0,planId:String?=null) {
        scope.launch {
            workingOrder=planId ?: purpose; error=""
            try { client.createOrder(accessToken,purpose,amount,planId); refresh() }
            catch(e:Exception) { error=e.message ?: "خطا در ساخت سفارش" }
            finally { workingOrder="" }
        }
    }
    val panel=Color(0xFF10243B)
    val accent=Color(0xFF8CC4FF)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onClose) { Icon(Icons.Default.ArrowBack,null,tint=accent) }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                Text(if(isEnglish) "EMA WALLET" else "کیف پول ایما",color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
                Text(if(isEnglish) "AI credit and Professional plan" else "اعتبار هوش مصنوعی و اشتراک حرفه‌ای",color=accent,style=MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.AccountBalanceWallet,null,tint=accent,modifier=Modifier.size(28.dp))
            IconButton(onClick={refresh()},enabled=!loading) { Icon(Icons.Default.Refresh,null,tint=accent) }
        }
        if(loading && snapshot==null) Box(Modifier.fillMaxWidth().padding(40.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator(color=accent) }
        snapshot?.let { data ->
            Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,accent.copy(alpha=.45f))) {
                Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(if(isEnglish) "Available AI credit" else "اعتبار قابل استفاده هوش مصنوعی",color=Color(0xFFB9C8D9))
                    Text(money(data.catalog.balanceToman),color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.headlineMedium)
                    Text(data.catalog.subscriptionName?.let { if(isEnglish) "Active plan: "+it else "اشتراک فعال: "+it } ?: if(isEnglish) "No active Professional plan" else "اشتراک حرفه‌ای فعالی نداری",color=accent,style=MaterialTheme.typography.bodySmall)
                }
            }
            Text(if(isEnglish) "Professional plans" else "اشتراک حرفه‌ای",color=Color.White,fontWeight=FontWeight.Bold)
            if(data.catalog.plans.isEmpty()) Text(if(isEnglish) "Plans are not available yet." else "فعلاً پلن فعالی برای خرید تنظیم نشده است.",color=Color.LightGray)
            data.catalog.plans.forEach { plan ->
                Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Button(onClick={createOrder("subscription",planId=plan.id)},enabled=workingOrder.isEmpty()) { Text(if(workingOrder==plan.id) "…" else if(isEnglish) "Order" else "ثبت سفارش") }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                            Text(plan.name,color=Color.White,fontWeight=FontWeight.SemiBold)
                            Text(money(plan.priceToman),color=accent)
                            Text(if(isEnglish) plan.durationDays.toString()+" days · "+plan.aiCreditPercent+"% AI credit" else plan.durationDays.toString()+" روز · "+plan.aiCreditPercent+"٪ اعتبار AI",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Text(if(isEnglish) "Top up AI credit" else "شارژ اعتبار هوش مصنوعی",color=Color.White,fontWeight=FontWeight.Bold)
            OutlinedTextField(value=topupAmount,onValueChange={topupAmount=it.filter(Char::isDigit).take(12)},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text(if(isEnglish) "Amount in toman" else "مبلغ به تومان")})
            Text((if(isEnglish) "Minimum: " else "حداقل: ")+money(data.catalog.minTopupToman)+" · "+(if(isEnglish) "Maximum: " else "حداکثر: ")+money(data.catalog.maxTopupToman),color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
            Button(onClick={topupAmount.toLongOrNull()?.let { createOrder("wallet_topup",it) }},enabled=workingOrder.isEmpty() && (topupAmount.toLongOrNull() ?: 0L)>0,modifier=Modifier.fillMaxWidth()) { Text(if(isEnglish) "Create top-up order" else "ساخت سفارش شارژ") }
            if(data.catalog.paymentInstructions.isNotBlank()) {
                Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),horizontalAlignment=Alignment.End) {
                        Text(if(isEnglish) "Payment instructions" else "اطلاعات پرداخت",color=accent,fontWeight=FontWeight.Bold)
                        Text(data.catalog.paymentInstructions,color=Color.White,textAlign=TextAlign.End)
                    }
                }
            }
            Text(if(isEnglish) "Payment orders" else "سفارش‌های پرداخت",color=Color.White,fontWeight=FontWeight.Bold)
            if(data.orders.isEmpty()) Text(if(isEnglish) "No payment orders yet." else "هنوز سفارشی ثبت نکرده‌ای.",color=Color.LightGray)
            data.orders.forEach { order ->
                Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp),horizontalAlignment=Alignment.End) {
                        Text(money(order.amountToman),color=Color.White,fontWeight=FontWeight.Bold)
                        Text((if(order.purpose=="subscription") if(isEnglish) "Subscription" else "اشتراک" else if(isEnglish) "AI wallet top-up" else "شارژ کیف پول")+" · "+when(order.status) {
                            "paid" -> if(isEnglish) "Paid" else "پرداخت تأیید شد"
                            "rejected" -> if(isEnglish) "Rejected" else "رد شد"
                            else -> if(isEnglish) "Pending" else "در انتظار بررسی"
                        },color=accent)
                        if(order.rejectionReason.isNotBlank()) Text(order.rejectionReason,color=Color(0xFFFFB2A8))
                        if(order.status=="pending" && !order.receiptSubmitted) {
                            OutlinedTextField(value=receiptDrafts[order.id].orEmpty(),onValueChange={receiptDrafts=receiptDrafts+(order.id to it)},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text(if(isEnglish) "Receipt / tracking number" else "شماره پیگیری رسید")})
                            Button(onClick={
                                scope.launch {
                                    workingOrder=order.id
                                    try { client.submitReceipt(accessToken,order.id,receiptDrafts[order.id].orEmpty()); receiptDrafts=receiptDrafts-order.id; refresh() }
                                    catch(e:Exception) { error=e.message ?: "ثبت رسید ناموفق بود." }
                                    finally { workingOrder="" }
                                }
                            },enabled=workingOrder.isEmpty() && receiptDrafts[order.id].orEmpty().trim().length>=3) { Text(if(isEnglish) "Submit receipt" else "ثبت رسید") }
                        } else if(order.receiptSubmitted) Text(if(isEnglish) "Receipt submitted · awaiting review" else "رسید ثبت شده · در انتظار بررسی مدیر",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if(error.isNotBlank()) Text(error,color=Color(0xFFFFB2A8),style=MaterialTheme.typography.bodySmall)
        if(loading && snapshot!=null) LinearProgressIndicator(Modifier.fillMaxWidth(),color=accent)
        Spacer(Modifier.height(16.dp))
    }
}
