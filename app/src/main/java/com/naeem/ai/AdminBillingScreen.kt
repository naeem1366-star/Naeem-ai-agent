package com.naeem.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AdminBillingScreen(client: BillingClient, accessToken: String, isEnglish: Boolean, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf<List<BillingAdminOrder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val accent = Color(0xFF8CC4FF)
    val panel = Color(0xFF10243B)
    fun money(value: Long) = NumberFormat.getNumberInstance(Locale.US).format(value) + if (isEnglish) " toman" else " تومان"
    fun refresh() {
        scope.launch {
            loading = true
            error = ""
            try { orders = client.adminOrders(accessToken) }
            catch (e: Exception) { error = e.message ?: if (isEnglish) "Could not load review queue." else "دریافت سفارش‌ها ناموفق بود." }
            finally { loading = false }
        }
    }
    LaunchedEffect(accessToken) { refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = accent) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(if (isEnglish) "PAYMENT REVIEW" else "مدیریت پرداخت‌ها", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(if (isEnglish) "Admin-only review queue" else "فقط برای حساب‌های مدیر", color = accent, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { refresh() }, enabled = !loading) { Icon(Icons.Default.Refresh, null, tint = accent) }
        }
        if (loading && orders.isEmpty()) Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
        if (!loading && orders.isEmpty() && error.isBlank()) Text(if (isEnglish) "No receipts waiting for review." else "رسیدی در انتظار بررسی نیست.", color = Color.LightGray)
        orders.forEach { order ->
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                    Text(money(order.amountToman), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(if (order.purpose == "subscription") if (isEnglish) "Subscription" else "اشتراک" else if (isEnglish) "Wallet top-up" else "شارژ کیف پول", color = accent)
                    Text((if (isEnglish) "User: " else "شناسه کاربر: ") + order.userId, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text((if (isEnglish) "Tracking: " else "شماره پیگیری: ") + order.reference.ifBlank { "—" }, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(order.createdAt.take(19).replace("T", " "), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    working = order.id
                                    error = ""
                                    try { client.reviewOrder(accessToken, order.id, "reject"); refresh() }
                                    catch (e: Exception) { error = e.message ?: "خطا در رد رسید" }
                                    finally { working = "" }
                                }
                            },
                            enabled = working.isEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (isEnglish) "Reject" else "رد رسید") }
                        Button(
                            onClick = {
                                scope.launch {
                                    working = order.id
                                    error = ""
                                    try { client.reviewOrder(accessToken, order.id, "approve"); refresh() }
                                    catch (e: Exception) { error = e.message ?: "خطا در تأیید پرداخت" }
                                    finally { working = "" }
                                }
                            },
                            enabled = working.isEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (working == order.id) "…" else if (isEnglish) "Approve & credit" else "تأیید و شارژ") }
                    }
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFFB2A8), style = MaterialTheme.typography.bodySmall)
    }
}
