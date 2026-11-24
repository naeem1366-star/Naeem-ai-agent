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
import java.util.Date
import java.util.Locale

@Composable
fun MonthlyWrappedDashboard(onClose: () -> Unit) {
    val context = LocalContext.current
    var period by remember { mutableStateOf("ماه") }
    val finance = remember { LedgerStore(context).entries() }
    val workouts = remember { SportStore(context).load() }
    val trips = remember { TravelStore(context).load() }
    val memories = remember { AssistantDataStore(context).memories() }
    val journalStore = remember { DailyJournalStore(context) }
    val now = remember { System.currentTimeMillis() }
    val periodStart = remember(period) {
        Calendar.getInstance().apply {
            timeInMillis = now
            if (period == "هفته") add(Calendar.DAY_OF_YEAR, -6)
            else set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val periodFinance = finance.filter { it.timestamp in periodStart..now }
    val periodWorkouts = workouts.filter { it.date in periodStart..now }
    val journalEntries = journalStore.report(periodStart, now + 1)
    val income = periodFinance.filter { it.isIncome }.sumOf { it.amountToman }
    val expenses = periodFinance.filterNot { it.isIncome }.sumOf { it.amountToman }
    val topCategory = periodFinance.filterNot { it.isIncome }.groupBy { it.category }
        .mapValues { (_, rows) -> rows.sumOf { it.amountToman } }.maxByOrNull { it.value }
    val travelExpenses = trips.sumOf { trip -> trip.expenses.sumOf { it.amount } }
    val activeDays = periodWorkouts.map { it.date / 86_400_000L }.distinct().size
    val journalGroups = journalEntries.groupBy { it.category }.toSortedMap()
    val number = remember { NumberFormat.getNumberInstance(Locale("fa", "IR")) }
    val periodName = if (period == "هفته") {
        "هفت روز اخیر"
    } else {
        java.text.SimpleDateFormat("LLLL yyyy", Locale("fa", "IR")).format(Date(now))
    }
    val report = remember(period, periodFinance, periodWorkouts, journalEntries, trips, memories) {
        buildString {
            append("گزارش ").append(if (period == "هفته") "هفتگی" else "ماهانه")
                .append(" Naeem AI — ").append(periodName).append("\n\n")
            append("درآمد: ").append(number.format(income)).append(" تومان\n")
            append("هزینه: ").append(number.format(expenses)).append(" تومان\n")
            append("مانده: ").append(number.format(income - expenses)).append(" تومان\n")
            append("جلسه تمرین: ").append(periodWorkouts.size).append(" • زمان: ")
                .append(periodWorkouts.sumOf { it.minutes }).append(" دقیقه\n")
            append("مسافت تمرین: ").append(number.format(periodWorkouts.sumOf { it.distance })).append(" کیلومتر\n")
            append("روزهای فعال ورزشی: ").append(activeDays).append("\n")
            append("هزینه سفرهای ثبت‌شده (کل سوابق): ").append(number.format(travelExpenses)).append(" تومان\n")
            append("\nروزنگار گفتگو:\n")
            if (journalGroups.isEmpty()) append("در این بازه رویدادی در روزنگار ثبت نشده.\n")
            journalGroups.forEach { (category, rows) ->
                append("• ").append(category).append(": ").append(rows.size).append(" مورد\n")
            }
            append("یادداشت‌های حافظه: ").append(memories.size)
            topCategory?.let { append("\nبیشترین دسته هزینه: ").append(it.key).append(" (").append(number.format(it.value)).append(" تومان)") }
        }
    }

    val bg = Color(0xFF0A0C10)
    val panel = Color(0xFF1B1F25)
    val accent = Color(0xFFD4D9E0)
    Surface(Modifier.fillMaxSize(), color = bg.copy(alpha = 0.84f)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = accent) }
                Column(Modifier.weight(1f)) {
                    Text("NAEEM AI / WRAPPED", color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("روایت تو", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(periodName, color = Color(0xFF9AA3AE), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("هفته", "ماه").forEach { item ->
                    FilterChip(
                        selected = period == item,
                        onClick = { period = item },
                        label = { Text(if (item == "هفته") "هفت روز اخیر" else "این ماه") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent, selectedLabelColor = bg,
                            containerColor = panel, labelColor = Color.White
                        )
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF252B34)), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("یک بازه، چند قدم از زندگی تو", color = accent, fontWeight = FontWeight.Bold)
                    Text("این گزارش از اطلاعاتی ساخته می‌شود که در بخش‌های متصل و روزنگار گفتگو ثبت کرده‌ای. موارد ثبت‌نشده در محاسبات ظاهر نمی‌شوند.", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text("مانده مالی این بازه", color = Color(0xFFBFC6D0), style = MaterialTheme.typography.labelMedium)
                    Text(number.format(income - expenses) + " تومان", color = if (income - expenses >= 0) accent else Color(0xFFFFA49B), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                WrappedMetric("درآمد", number.format(income), "تومان", Modifier.weight(1f), accent, panel)
                WrappedMetric("هزینه", number.format(expenses), "تومان", Modifier.weight(1f), Color(0xFFFFA49B), panel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                WrappedMetric("جلسه تمرین", periodWorkouts.size.toString(), "در این بازه", Modifier.weight(1f), Color(0xFFBFC6D0), panel)
                WrappedMetric("زمان تمرین", periodWorkouts.sumOf { it.minutes }.toString(), "دقیقه", Modifier.weight(1f), Color(0xFFBFC6D0), panel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                WrappedMetric("مسافت", number.format(periodWorkouts.sumOf { it.distance }), "کیلومتر", Modifier.weight(1f), Color(0xFFBFC6D0), panel)
                WrappedMetric("روز فعال", activeDays.toString(), "ورزشی", Modifier.weight(1f), Color(0xFFBFC6D0), panel)
            }
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("روزنگار هوشمند", color = accent, fontWeight = FontWeight.Bold)
                    if (journalEntries.isEmpty()) {
                        Text("هنوز روایتی برای این بازه ثبت نشده. می‌توانی اتفاق‌های روزت را در گفتگو تعریف کنی.", color = Color(0xFFB8C2CC))
                    } else {
                        Text(journalEntries.size.toString() + " رویداد ثبت‌شده در " + journalGroups.size + " دسته", color = Color.White)
                        journalGroups.forEach { (category, rows) ->
                            Text(category + " • " + rows.size + " مورد", color = accent, fontWeight = FontWeight.SemiBold)
                            rows.take(3).forEach { row ->
                                Text("– " + row.title + ": " + row.detail, color = Color(0xFFB8C2CC), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("سوابق دیگر", color = accent, fontWeight = FontWeight.Bold)
                    Text(trips.size.toString() + " سفر ثبت‌شده • " + number.format(travelExpenses) + " تومان هزینه در کل سفرها", color = Color.White)
                    Text(memories.size.toString() + " یادداشت در حافظه شخصی", color = Color(0xFFB8C2CC))
                    Text("بیشترین دسته هزینه این بازه: " + (topCategory?.key ?: "هنوز مشخص نیست"), color = Color(0xFFBFC6D0))
                }
            }
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(intent, "اشتراک گزارش"))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = bg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("اشتراک گزارش", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WrappedMetric(title: String, value: String, unit: String, modifier: Modifier, tint: Color, panel: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color(0xFF9AA3AE), style = MaterialTheme.typography.labelSmall)
            Text(value, color = tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(unit, color = Color(0xFF8995A1), style = MaterialTheme.typography.labelSmall)
        }
    }
}
