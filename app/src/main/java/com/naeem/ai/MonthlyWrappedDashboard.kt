package com.naeem.ai

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MonthlyWrappedDashboard(onClose: () -> Unit) {
    val context = LocalContext.current
    val finance = remember { LedgerStore(context).entries() }
    val workouts = remember { SportStore(context).load() }
    val trips = remember { TravelStore(context).load() }
    val memories = remember { AssistantDataStore(context).memories() }
    val monthStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val monthFinance = finance.filter { it.timestamp >= monthStart }
    val monthWorkouts = workouts.filter { it.date >= monthStart }
    val income = monthFinance.filter { it.isIncome }.sumOf { it.amountToman }
    val expenses = monthFinance.filterNot { it.isIncome }.sumOf { it.amountToman }
    val topCategory = monthFinance.filterNot { it.isIncome }.groupBy { it.category }
        .mapValues { (_, rows) -> rows.sumOf { it.amountToman } }.maxByOrNull { it.value }
    val travelExpenses = trips.sumOf { trip -> trip.expenses.sumOf { it.amount } }
    val activeDays = monthWorkouts.map { it.date / 86_400_000L }.distinct().size
    val number = remember { NumberFormat.getNumberInstance(Locale("fa", "IR")) }
    val monthName = remember { java.text.SimpleDateFormat("LLLL yyyy", Locale("fa", "IR")).format(java.util.Date()) }
    val report = remember(monthFinance, monthWorkouts, trips, memories) {
        buildString {
            append("گزارش ماهانه Naeem AI — ").append(monthName).append("\n\n")
            append("درآمد: ").append(number.format(income)).append(" تومان\n")
            append("هزینه: ").append(number.format(expenses)).append(" تومان\n")
            append("مانده: ").append(number.format(income - expenses)).append(" تومان\n")
            append("جلسه تمرین: ").append(monthWorkouts.size).append(" • زمان: ").append(monthWorkouts.sumOf { it.minutes }).append(" دقیقه\n")
            append("مسافت تمرین: ").append(number.format(monthWorkouts.sumOf { it.distance })).append(" کیلومتر\n")
            append("روزهای فعال ورزشی: ").append(activeDays).append("\n")
            append("هزینه سفرهای ثبت‌شده: ").append(number.format(travelExpenses)).append(" تومان\n")
            append("یادداشت‌های حافظه: ").append(memories.size)
            topCategory?.let { append("\nبیشترین دسته هزینه: ").append(it.key).append(" (").append(number.format(it.value)).append(" تومان)") }
        }
    }

    val bg = Color(0xFF10141B)
    val panel = Color(0xFF202731)
    val accent = Color(0xFF9BE5C4)
    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = accent) }
                Column(Modifier.weight(1f)) {
                    Text("NAEEM AI / WRAPPED", color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("روایت ماه تو", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(monthName, color = Color(0xFFAEB9C2), style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1D3932)), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("یک ماه، چند قدم جلوتر", color = accent, fontWeight = FontWeight.Bold)
                    Text("این گزارش از اطلاعاتی ساخته شده که خودت در Naeem AI ثبت کرده‌ای؛ پیش‌بینی یا قضاوت نیست.", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text("ماندهٔ مالی ماه", color = Color(0xFFB8C9C2), style = MaterialTheme.typography.labelMedium)
                    Text(number.format(income - expenses) + " تومان", color = if (income - expenses >= 0) accent else Color(0xFFFFA49B), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                WrappedMetric("درآمد", number.format(income), "تومان", Modifier.weight(1f), accent, panel)
                WrappedMetric("هزینه", number.format(expenses), "تومان", Modifier.weight(1f), Color(0xFFFFA49B), panel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                WrappedMetric("جلسه تمرین", monthWorkouts.size.toString(), "این ماه", Modifier.weight(1f), Color(0xFFC7B8FF), panel)
                WrappedMetric("زمان تمرین", monthWorkouts.sumOf { it.minutes }.toString(), "دقیقه", Modifier.weight(1f), Color(0xFF8CCCF1), panel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                WrappedMetric("مسافت", number.format(monthWorkouts.sumOf { it.distance }), "کیلومتر", Modifier.weight(1f), Color(0xFF8CCCF1), panel)
                WrappedMetric("روز فعال", activeDays.toString(), "ورزشی", Modifier.weight(1f), Color(0xFFC7B8FF), panel)
            }
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ردپای سفر و حافظه", color = accent, fontWeight = FontWeight.Bold)
                    Text(trips.size.toString() + " سفر ثبت‌شده • " + number.format(travelExpenses) + " تومان هزینه سفر", color = Color.White)
                    Text(memories.size.toString() + " یادداشت در حافظه شخصی", color = Color(0xFFB8C2CC))
                    Text("بیشترین دسته هزینه: " + (topCategory?.key ?: "هنوز مشخص نیست"), color = Color(0xFFFFD08A))
                }
            }
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(intent, "اشتراک گزارش ماهانه"))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = bg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("اشتراک گزارش ماهانه", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WrappedMetric(title: String, value: String, unit: String, modifier: Modifier, tint: Color, panel: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color(0xFFAEB9C2), style = MaterialTheme.typography.labelSmall)
            Text(value, color = tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(unit, color = Color(0xFF8995A1), style = MaterialTheme.typography.labelSmall)
        }
    }
}
