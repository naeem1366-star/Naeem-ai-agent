package com.naeem.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AdminBillingScreen(client: BillingClient, accessToken: String, isEnglish: Boolean, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<BillingAdminDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("overview") }
    val accent = Color(0xFF8CC4FF)
    val panel = Color(0xFF10243B)
    val muted = Color(0xFFB9C8D9)
    fun money(value: Long) = NumberFormat.getNumberInstance(Locale.US).format(value) + if (isEnglish) " toman" else " تومان"
    fun refresh() {
        scope.launch {
            loading = true
            error = ""
            try { dashboard = client.adminDashboard(accessToken) }
            catch (e: Exception) { error = e.message ?: if (isEnglish) "Could not load admin dashboard." else "دریافت اطلاعات پنل مدیریت ناموفق بود." }
            finally { loading = false }
        }
    }
    LaunchedEffect(accessToken) { refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = accent) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(if (isEnglish) "EMA OWNER CONSOLE" else "مرکز مدیریت ایما", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(if (isEnglish) "Private owner workspace" else "پنل اختصاصی مالک مجموعه", color = accent, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { refresh() }, enabled = !loading) { Icon(Icons.Default.Refresh, null, tint = accent) }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val tabs = listOf(
                "overview" to if (isEnglish) "Overview" else "نمای کلی",
                "payments" to if (isEnglish) "Payments" else "پرداخت‌ها",
                "users" to if (isEnglish) "Users" else "کاربران",
                "subscriptions" to if (isEnglish) "Subscriptions" else "اشتراک‌ها",
                "costs" to if (isEnglish) "AI costs" else "هزینه هوش مصنوعی",
                "audit" to if (isEnglish) "Activity log" else "گزارش فعالیت"
            )
            tabs.forEach { (key, label) ->
                FilterChip(selected = section == key, onClick = { section = key }, label = { Text(label) })
            }
        }

        if (loading && dashboard == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
        }
        dashboard?.let { data ->
            val s = data.summary
            when (section) {
                "overview" -> {
                    Text(if (isEnglish) "Organization snapshot" else "وضعیت کلی مجموعه", color = Color.White, fontWeight = FontWeight.Bold)
                    MetricCard(if (isEnglish) "Registered users" else "کل کاربران", NumberFormat.getNumberInstance(Locale.US).format(s.optLong("users_total")), accent, panel)
                    MetricCard(if (isEnglish) "Total user wallet balances" else "مجموع اعتبار باقی‌مانده کاربران", money(s.optLong("wallet_balance_total_toman")), accent, panel)
                    MetricCard(if (isEnglish) "Confirmed revenue · all time" else "کل پرداخت‌های تأییدشده", money(s.optLong("paid_revenue_total_toman")), Color(0xFF9BE7C4), panel)
                    MetricCard(if (isEnglish) "Confirmed revenue · this month" else "درآمد تأییدشده این ماه", money(s.optLong("paid_revenue_month_toman")), Color(0xFF9BE7C4), panel)
                    MetricCard(if (isEnglish) "Active subscriptions" else "اشتراک‌های فعال", NumberFormat.getNumberInstance(Locale.US).format(s.optLong("subscriptions_active")), accent, panel)
                    MetricCard(if (isEnglish) "Receipts awaiting review" else "رسیدهای در انتظار بررسی", NumberFormat.getNumberInstance(Locale.US).format(s.optLong("receipts_waiting_count")) + " · " + money(s.optLong("receipts_waiting_amount_toman")), Color(0xFFFFD18A), panel)
                    MetricCard(if (isEnglish) "Orders awaiting payment" else "سفارش‌های پرداخت‌نشده", NumberFormat.getNumberInstance(Locale.US).format(s.optLong("orders_pending_count")) + " · " + money(s.optLong("orders_pending_amount_toman")), muted, panel)
                    MetricCard(if (isEnglish) "Tracked provider cost · USD" else "هزینه ثبت‌شده سرویس‌ها · دلار", "$" + String.format(Locale.US, "%.4f", s.optDouble("provider_cost_total_usd")), muted, panel)
                    Text(if (isEnglish) "Wallet balances are user credit liabilities, not company revenue. Provider costs show only usage recorded in EMA's cost ledger." else "موجودی کیف پول کاربران درآمد مجموعه نیست؛ اعتبار باقی‌مانده کاربران است. هزینه سرویس‌ها هم فقط مصرف‌هایی را نشان می‌دهد که در دفتر هزینه ایما ثبت شده‌اند.", color = muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text(if (isEnglish) "Latest 200 payments · latest 100 users and subscriptions are available in their tabs." else "در زبانه‌ها آخرین ۲۰۰ سفارش و آخرین ۱۰۰ کاربر و اشتراک نمایش داده می‌شوند.", color = muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                }
                "payments" -> {
                    Text(if (isEnglish) "Payment orders" else "تمام سفارش‌های پرداخت", color = Color.White, fontWeight = FontWeight.Bold)
                    data.paymentStatus.forEach { item ->
                        Text(item.optString("status") + " · " + item.optLong("order_count") + " · " + money(item.optLong("amount_toman")), color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    data.orders.forEach { order ->
                        AdminInfoCard(panel) {
                            Text(money(order.optLong("amount_toman")), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(order.optString("email").ifBlank { order.optString("user_id") }, color = accent, style = MaterialTheme.typography.bodySmall)
                            Text(
                                (if (order.optString("order_type") == "subscription") (order.optString("plan_name").ifBlank { if (isEnglish) "Subscription" else "اشتراک" }) else if (isEnglish) "Wallet top-up" else "شارژ کیف پول") +
                                    " · " + order.optString("status"),
                                color = Color.White
                            )
                            Text((if (isEnglish) "Order: " else "شناسه سفارش: ") + order.optString("id"), color = muted, style = MaterialTheme.typography.bodySmall)
                            Text((if (isEnglish) "Tracking: " else "پیگیری: ") + order.optString("payment_reference").ifBlank { "—" }, color = muted, style = MaterialTheme.typography.bodySmall)
                            Text(order.optString("created_at").take(19).replace("T", " "), color = muted, style = MaterialTheme.typography.bodySmall)
                            if (order.optString("status") == "receipt_submitted") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { review(order.optString("id"), "reject", client, accessToken, scope, { working = it }, { error = it }, { refresh() }) },
                                        enabled = working.isEmpty(), modifier = Modifier.weight(1f)
                                    ) { Text(if (isEnglish) "Reject" else "رد رسید") }
                                    Button(
                                        onClick = { review(order.optString("id"), "approve", client, accessToken, scope, { working = it }, { error = it }, { refresh() }) },
                                        enabled = working.isEmpty(), modifier = Modifier.weight(1f)
                                    ) { Text(if (working == order.optString("id")) "…" else if (isEnglish) "Approve & credit" else "تأیید و اعمال اعتبار") }
                                }
                            }
                        }
                    }
                    if (data.orders.isEmpty()) Text(if (isEnglish) "No orders yet." else "هنوز سفارشی ثبت نشده است.", color = muted)
                }
                "users" -> {
                    Text(if (isEnglish) "User accounts · latest 100" else "حساب کاربران · ۱۰۰ مورد اخیر", color = Color.White, fontWeight = FontWeight.Bold)
                    data.users.forEach { user ->
                        AdminInfoCard(panel) {
                            Text(user.optString("email").ifBlank { user.optString("user_id") }, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text((if (isEnglish) "Wallet credit: " else "اعتبار کیف پول: ") + money(user.optLong("balance_toman")), color = accent)
                            Text((if (isEnglish) "Status: " else "وضعیت: ") + user.optString("status") + " · " + (if (isEnglish) "Active plans: " else "اشتراک فعال: ") + user.optLong("active_subscriptions"), color = muted, style = MaterialTheme.typography.bodySmall)
                            Text((if (isEnglish) "Joined: " else "تاریخ عضویت: ") + user.optString("created_at").take(10), color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                "subscriptions" -> {
                    Text(if (isEnglish) "Subscriptions · latest 100" else "اشتراک‌ها · ۱۰۰ مورد اخیر", color = Color.White, fontWeight = FontWeight.Bold)
                    data.subscriptions.forEach { sub ->
                        AdminInfoCard(panel) {
                            Text(sub.optString("email").ifBlank { sub.optString("user_id") }, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(sub.optString("plan_name").ifBlank { sub.optString("plan_id") }, color = accent)
                            Text((if (sub.optBoolean("is_active")) if (isEnglish) "Active" else "فعال" else if (isEnglish) "Expired / inactive" else "پایان‌یافته / غیرفعال"), color = muted)
                            Text(sub.optString("starts_at").take(10) + " → " + sub.optString("ends_at").take(10), color = muted, style = MaterialTheme.typography.bodySmall)
                            Text((if (isEnglish) "Payment order: " else "سفارش پرداخت: ") + sub.optString("payment_order_id").ifBlank { "—" }, color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                "costs" -> {
                    Text(if (isEnglish) "Provider cost ledger" else "دفتر هزینه سرویس‌های هوش مصنوعی", color = Color.White, fontWeight = FontWeight.Bold)
                    MetricCard(if (isEnglish) "All-time tracked cost" else "کل هزینه ثبت‌شده", "$" + String.format(Locale.US, "%.4f", s.optDouble("provider_cost_total_usd")), accent, panel)
                    MetricCard(if (isEnglish) "This month tracked cost" else "هزینه ثبت‌شده این ماه", "$" + String.format(Locale.US, "%.4f", s.optDouble("provider_cost_month_usd")), accent, panel)
                    data.providerCosts.forEach { item ->
                        AdminInfoCard(panel) {
                            Text(item.optString("provider"), color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("$" + String.format(Locale.US, "%.6f", item.optDouble("cost_usd")), color = accent)
                            Text((if (isEnglish) "Recorded events: " else "تعداد رویدادهای ثبت‌شده: ") + item.optLong("event_count"), color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(if (isEnglish) "This is EMA's recorded usage cost, not a live provider account balance or invoice." else "این عدد هزینه مصرف ثبت‌شده در ایماست؛ موجودی زنده یا صورتحساب پنل GapGPT و NetArz نیست.", color = muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                }
                "audit" -> {
                    Text(if (isEnglish) "Recent administrative activity" else "آخرین فعالیت‌های مدیریتی", color = Color.White, fontWeight = FontWeight.Bold)
                    data.recentAudit.forEach { item ->
                        AdminInfoCard(panel) {
                            Text(item.optString("action"), color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text((if (isEnglish) "Target user: " else "کاربر مرتبط: ") + item.optString("target_user_id").ifBlank { "—" }, color = accent, style = MaterialTheme.typography.bodySmall)
                            Text(item.optString("created_at").take(19).replace("T", " "), color = muted, style = MaterialTheme.typography.bodySmall)
                            Text(item.optJSONObject("details")?.toString()?.take(240).orEmpty(), color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFFB2A8), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        if (loading && dashboard != null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MetricCard(title: String, value: String, accent: Color, panel: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, accent.copy(alpha = .28f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color(0xFFB9C8D9), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun AdminInfoCard(panel: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End, content = content)
    }
}

private fun review(
    orderId: String,
    decision: String,
    client: BillingClient,
    accessToken: String,
    scope: kotlinx.coroutines.CoroutineScope,
    setWorking: (String) -> Unit,
    setError: (String) -> Unit,
    refresh: () -> Unit
) {
    scope.launch {
        setWorking(orderId)
        setError("")
        try {
            client.reviewOrder(accessToken, orderId, decision)
            refresh()
        } catch (e: Exception) {
            setError(e.message ?: "Payment review failed.")
        } finally {
            setWorking("")
        }
    }
}
