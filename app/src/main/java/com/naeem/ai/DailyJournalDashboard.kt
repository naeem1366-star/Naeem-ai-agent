package com.naeem.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyJournalDashboard(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { DailyJournalStore(context.applicationContext) }
    var entries by remember { mutableStateOf(store.entries()) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("همه") }
    val categories = listOf("همه") + entries.map { it.category }.distinct()
    val filtered = entries.filter {
        (selectedCategory == "همه" || it.category == selectedCategory) &&
            (query.isBlank() || it.title.contains(query, true) || it.detail.contains(query, true) || it.category.contains(query, true))
    }
    val background = Color(0xFF0A0C10)
    val panel = Color(0xFF1B2028)
    val accent = Color(0xFFD7E0ED)
    val muted = Color(0xFF9AA3AE)

    Column(Modifier.fillMaxSize().background(background).padding(horizontal = 17.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PERSONAL JOURNAL", color = muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("روزنگار من", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${entries.size} رویداد ذخیره‌شده", color = muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "بستن", tint = accent) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("جست‌وجو در روزنگار") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.take(4).forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (entries.isEmpty()) "هنوز رویدادی ثبت نشده است." else "موردی با این جست‌وجو پیدا نشد.", color = muted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(17.dp)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(item.category + "  •  " + SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa", "IR")).format(Date(item.timestamp)), color = muted, style = MaterialTheme.typography.labelSmall)
                                Text(item.detail, color = accent, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { store.remove(item.id); entries = store.entries() }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف رویداد", tint = Color(0xFFFF9A91))
                            }
                        }
                    }
                }
            }
        }
    }
}
