package com.naeem.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SecureConfigStore(this)
        setContent { NaeemApp(store) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaeemApp(store: SecureConfigStore) {
    var showSettings by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(store.baseUrl()) }
    var model by remember { mutableStateOf(store.model()) }
    var apiKey by remember { mutableStateOf(store.apiKey()) }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val client = remember { ChatClient() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Naeem AI") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "تنظیمات")
                        }
                    }
                )
            },
            bottomBar = {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = prompt, onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("پیامت رو بنویس…") },
                        maxLines = 4, shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = !busy && prompt.isNotBlank(), onClick = {
                        val question = prompt.trim()
                        prompt = ""
                        messages.add(ChatMessage(question, true))
                        busy = true
                        error = ""
                        scope.launch {
                            try {
                                val answer = client.send(baseUrl, model, apiKey, question)
                                messages.add(ChatMessage(answer, false))
                            } catch (e: Exception) {
                                error = e.message ?: "خطای نامشخص"
                            } finally { busy = false }
                        }
                    }) { Text("ارسال") }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                Text("دستیار هوش مصنوعی فارسی", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 10.dp))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(messages) { msg ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.fromUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(msg.text, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("تنظیمات اتصال") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("نشانی سرویس (Base URL)")
                        OutlinedTextField(baseUrl, { baseUrl = it }, singleLine = true)
                        Text("مدل")
                        OutlinedTextField(model, { model = it }, singleLine = true)
                        Text("کلید API")
                        OutlinedTextField(apiKey, { apiKey = it }, singleLine = true)
                        Text("کلید فقط روی همین دستگاه ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        store.save(baseUrl, model, apiKey)
                        showSettings = false
                    }) { Text("ذخیره") }
                },
                dismissButton = { TextButton(onClick = { showSettings = false }) { Text("انصراف") } }
            )
        }
    }
}
