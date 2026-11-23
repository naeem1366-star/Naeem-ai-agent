package com.naeem.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FinanceGold = Color(0xFFD9B77A)
private val FinancePanel = Color(0xFF20232A)

@Composable
fun FinanceDashboard(entries: List<LedgerEntry>, onAdd: (Long, String, String, Boolean) -> Unit, onDelete: (Long) -> Unit, onClose: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("خوراک") }
    var note by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    val monthStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val monthEntries = entries.filter { it.timestamp >= monthStart }
    val expenses = monthEntries.filterNot { it.isIncome }.sumOf { it.amountToman }
    val incomes = monthEntries.filter { it.isIncome }.sumOf { it.amountToman }
    val categories = monthEntries.filterNot { it.isIncome }.groupBy { it.category }
        .mapValues { (_, values) -> values.sumOf { it.amountToman } }.toList().sortedByDescending { it.second }
    val number = remember { NumberFormat.getNumberInstance(Locale.US) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa", "IR")) }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("دفتر مالی نعیم", style = MaterialTheme.typography.headlineSmall, color = FinanceGold, fontWeight = FontWeight.Bold)
                Text("ثبت و تحلیل دخل‌وخرج، روی همین گوشی", color = Color(0xFFAAAEB8), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClose) { Text("بستن", color = FinanceGold) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FinanceStat("هزینهٔ این ماه", expenses, Modifier.weight(1f), Color(0xFFFFA49B), number)
            FinanceStat("درآمد این ماه", incomes, Modifier.weight(1f), Color(0xFF9BD6B0), number)
        }
        Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = FinancePanel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("ماندهٔ خالص ماه", color = Color(0xFFB9BDC6), style = MaterialTheme.typography.labelMedium)
                Text("${number.format(incomes - expenses)} تومان", color = if (incomes - expenses >= 0) Color(0xFF9BD6B0) else Color(0xFFFFA49B), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FinancePanel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ثبت تراکنش", color = FinanceGold, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !income, onClick = { income = false }, label = { Text("هزینه") })
                    FilterChip(selected = income, onClick = { income = true }, label = { Text("درآمد") })
                }
                OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '٬' } }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("مبلغ به تومان") })
                OutlinedTextField(value = category, onValueChange = { category = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("دسته‌بندی (مثلاً خوراک، خودرو، خانه)") })
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("شرح اختیاری") })
                Button(onClick = {
                    val amount = amountText.replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
                    if (amount > 0) { onAdd(amount, if (income) "درآمد" else category, note, income); amountText = ""; note = "" }
                }, modifier = Modifier.fillMaxWidth(), enabled = amountText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = FinanceGold, contentColor = Color(0xFF171717))) {
                    Text("ذخیرهٔ تراکنش")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("بیشترین هزینه‌ها در این ماه", color = FinanceGold, fontWeight = FontWeight.SemiBold)
        if (categories.isEmpty()) Text("هنوز هزینه‌ای برای این ماه ثبت نشده.", color = Color(0xFFAAAEB8), modifier = Modifier.padding(vertical = 8.dp))
        categories.take(5).forEach { (name, total) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, color = Color.White)
                Text("${number.format(total)} تومان", color = Color(0xFFFFC98A), fontWeight = FontWeight.Medium)
            }
            LinearProgressIndicator(progress = { if (expenses > 0) total.toFloat() / expenses.toFloat() else 0f }, modifier = Modifier.fillMaxWidth(), color = FinanceGold, trackColor = FinancePanel)
        }
        Spacer(Modifier.height(12.dp))
        Text("تراکنش‌های اخیر", color = FinanceGold, fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(entries.take(100), key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = FinancePanel), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.note.ifBlank { item.category }, color = Color.White, fontWeight = FontWeight.Medium)
                            Text("${item.category} • ${dateFormat.format(Date(item.timestamp))}", color = Color(0xFFAAAEB8), style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${if (item.isIncome) "+" else "−"}${number.format(item.amountToman)}", color = if (item.isIncome) Color(0xFF9BD6B0) else Color(0xFFFFA49B), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { onDelete(item.id) }, contentPadding = PaddingValues(0.dp)) { Text("حذف", color = Color(0xFFFFA49B), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceStat(title: String, amount: Long, modifier: Modifier, tint: Color, number: NumberFormat) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = FinancePanel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Color(0xFFB9BDC6), style = MaterialTheme.typography.labelSmall)
            Text(number.format(amount), color = tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("تومان", color = Color(0xFFAAAEB8), style = MaterialTheme.typography.labelSmall)
        }
    }
}
