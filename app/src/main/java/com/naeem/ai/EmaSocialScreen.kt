package com.naeem.ai

import androidx.compose.foundation.BorderStroke
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
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun EmaSocialScreen(
    accessToken: String,
    isEnglish: Boolean,
    onClose: () -> Unit
) {
    val client = remember { EmaSocialClient() }
    val scope = rememberCoroutineScope()
    val accent = Color(0xFF8DD9E8)
    val panel = Color(0xFF10243B)
    val muted = Color(0xFFB9C8D9)
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("profile") }
    var enabled by remember { mutableStateOf(false) }
    var maintenance by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(EmaSocialProfile()) }
    var profileConsent by remember { mutableStateOf(false) }
    var semanticConsent by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<List<EmaSocialPerson>>(emptyList()) }
    var requests by remember { mutableStateOf(JSONArray()) }

    fun refresh() {
        scope.launch {
            loading = true
            error = ""
            try {
                val state = client.status(accessToken)
                enabled = state.enabled
                maintenance = state.maintenanceMessage
                profile = runCatching { client.profile(accessToken) }.getOrNull() ?: profile
                profileConsent = profile.profileProcessingConsent
                semanticConsent = profile.semanticMatchingConsent
                if (enabled) {
                    if (tab == "discover") people = client.discover(accessToken)
                    if (tab == "matches") people = client.matches(accessToken)
                    if (tab == "connections") requests = client.requests(accessToken)
                }
            } catch (e: Exception) {
                error = e.message ?: if (isEnglish) "Could not connect to EMA Social." else "اتصال به شبکه اجتماعی برقرار نشد."
            } finally { loading = false }
        }
    }
    LaunchedEffect(accessToken) { refresh() }
    LaunchedEffect(tab, enabled) {
        if (enabled && !loading) {
            runCatching {
                when (tab) {
                    "discover" -> people = client.discover(accessToken)
                    "matches" -> people = client.matches(accessToken)
                    "connections" -> requests = client.requests(accessToken)
                }
            }.onFailure { error = it.message ?: "خطا در دریافت اطلاعات" }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = accent) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("EMA SOCIAL", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Connecting Minds. Creating Futures.", color = accent, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { refresh() }, enabled = !loading) { Icon(Icons.Default.Refresh, null, tint = accent) }
        }

        if (!enabled) {
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isEnglish) "EMA Social is temporarily unavailable" else "شبکه اجتماعی ایما موقتاً در دسترس نیست", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(maintenance.ifBlank { if (isEnglish) "This service is currently disabled by its administrator." else "این سرویس در حال حاضر توسط مدیریت غیرفعال شده است." }, color = muted, textAlign = TextAlign.Center)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("profile" to if (isEnglish) "Profile" else "پروفایل",
                    "discover" to if (isEnglish) "Discover" else "کشف افراد",
                    "matches" to "Match", "connections" to if (isEnglish) "Connect" else "ارتباطات").forEach { (key, label) ->
                    FilterChip(selected = tab == key, onClick = { tab = key }, label = { Text(label) })
                }
            }
            when (tab) {
                "profile" -> {
                    Text(if (isEnglish) "Your professional identity" else "هویت شخصی و حرفه‌ای تو", color = Color.White, fontWeight = FontWeight.Bold)
                    @Composable fun field(label: String, value: String, update: (String) -> Unit, lines: Int = 1) {
                        OutlinedTextField(value, update, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, minLines = lines,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    }
                    field(if (isEnglish) "Display name" else "نام نمایشی", profile.displayName, { profile = profile.copy(displayName = it) })
                    field(if (isEnglish) "Professional headline" else "عنوان تخصصی", profile.headline, { profile = profile.copy(headline = it) })
                    field(if (isEnglish) "About you" else "معرفی کوتاه", profile.bio, { profile = profile.copy(bio = it) }, 3)
                    field(if (isEnglish) "City" else "شهر", profile.city, { profile = profile.copy(city = it) })
                    field(if (isEnglish) "Interests · comma separated" else "علایق · با ویرگول جدا کن", profile.interests.joinToString("، "), { profile = profile.copy(interests = it.split(',', '،').map(String::trim).filter(String::isNotBlank)) })
                    field(if (isEnglish) "Skills · comma separated" else "مهارت‌ها · با ویرگول جدا کن", profile.skills.joinToString("، "), { profile = profile.copy(skills = it.split(',', '،').map(String::trim).filter(String::isNotBlank)) })
                    field(if (isEnglish) "Goals · comma separated" else "اهداف · با ویرگول جدا کن", profile.goals.joinToString("، "), { profile = profile.copy(goals = it.split(',', '،').map(String::trim).filter(String::isNotBlank)) })
                    field(if (isEnglish) "Experience" else "تجربه کاری", profile.experienceSummary, { profile = profile.copy(experienceSummary = it) }, 3)
                    Text(if (isEnglish) "Profile visibility" else "نمایش پروفایل", color = accent, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("private" to if (isEnglish) "Private" else "خصوصی", "connections" to if (isEnglish) "Connections" else "ارتباطات", "public" to if (isEnglish) "Public" else "عمومی").forEach { (v,l) ->
                            FilterChip(selected = profile.visibility == v, onClick = { profile = profile.copy(visibility = v) }, label = { Text(l) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isEnglish) "Allow my profile in Discover" else "نمایش در بخش کشف افراد", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(profile.discoverable, { profile = profile.copy(discoverable = it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isEnglish) "Allow compatibility matching" else "اجازه استفاده از اطلاعات برای تطبیق هوشمند", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(profile.aiMatchingConsent, { profile = profile.copy(aiMatchingConsent = it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isEnglish) "I consent to profile processing" else "با پردازش اطلاعات پروفایل موافقم", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(profileConsent, { profileConsent = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isEnglish) "I consent to semantic matching" else "با تطبیق معنایی موافقم", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(semanticConsent, { semanticConsent = it })
                    }
                    Button(onClick = {
                        scope.launch {
                            working = true; error = ""
                            try { client.saveProfile(accessToken, profile, profileConsent, semanticConsent); notice = if (isEnglish) "Profile saved." else "پروفایل با موفقیت ذخیره شد." }
                            catch (e: Exception) { error = e.message ?: "ذخیره پروفایل ناموفق بود." }
                            finally { working = false }
                        }
                    }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(if (working) "…" else if (isEnglish) "Save profile" else "ذخیره پروفایل") }
                }
                "discover", "matches" -> {
                    Text(if (tab == "discover") (if (isEnglish) "People you can discover" else "افرادی که می‌توانی کشف کنی") else (if (isEnglish) "Compatibility suggestions" else "پیشنهادهای تطبیق هوشمند"), color = Color.White, fontWeight = FontWeight.Bold)
                    if (tab == "matches") Text(if (isEnglish) "Scores are estimates, not guarantees." else "امتیازها تخمینی هستند و موفقیت همکاری را تضمین نمی‌کنند.", color = muted, style = MaterialTheme.typography.bodySmall)
                    people.forEach { p ->
                        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(p.displayName.ifBlank { if (isEnglish) "EMA member" else "عضو ایما" }, color = Color.White, fontWeight = FontWeight.Bold)
                                if (p.headline.isNotBlank()) Text(p.headline, color = accent)
                                if (p.city.isNotBlank()) Text(p.city, color = muted, style = MaterialTheme.typography.bodySmall)
                                if (p.bio.isNotBlank()) Text(p.bio, color = Color.White)
                                if (p.skills.isNotEmpty()) Text((if (isEnglish) "Skills: " else "مهارت‌ها: ") + p.skills.joinToString("، "), color = muted)
                                if (p.interests.isNotEmpty()) Text((if (isEnglish) "Interests: " else "علایق: ") + p.interests.joinToString("، "), color = muted)
                                if (tab == "matches") {
                                    Text((if (isEnglish) "Estimated fit: " else "تطبیق تخمینی: ") + p.score + "%", color = accent, fontWeight = FontWeight.Bold)
                                    p.reasons.forEach { Text("• $it", color = muted, style = MaterialTheme.typography.bodySmall) }
                                }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        working = true
                                        try { client.sendRequest(accessToken, p.userId, if (isEnglish) "Hello, I'd like to connect on EMA." else "سلام، دوست دارم در ایما با شما ارتباط برقرار کنم."); notice = if (isEnglish) "Connection request sent." else "درخواست ارتباط ارسال شد." }
                                        catch (e: Exception) { error = e.message ?: "ارسال درخواست ناموفق بود." }
                                        finally { working = false }
                                    }
                                }, enabled = !working) { Text(if (isEnglish) "Connect" else "درخواست ارتباط") }
                            }
                        }
                    }
                    if (people.isEmpty() && !loading) Text(if (isEnglish) "No matching public profiles yet." else "هنوز پروفایل عمومی مرتبطی پیدا نشد.", color = muted)
                }
                "connections" -> {
                    Text(if (isEnglish) "Connection requests" else "درخواست‌های ارتباط", color = Color.White, fontWeight = FontWeight.Bold)
                    (0 until requests.length()).forEach { i ->
                        val r = requests.optJSONObject(i) ?: JSONObject()
                        Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(if (r.optString("sender_user_id") == r.optString("recipient_user_id")) "—" else r.optString("sender_user_id").take(8) + "…", color = Color.White)
                                Text(r.optString("status"), color = muted)
                                if (r.optString("direction") == "incoming" && r.optString("status") == "pending") {
                                    Row {
                                        TextButton(onClick = { scope.launch { runCatching { client.updateRequest(accessToken, r.optString("id"), "accepted"); requests = client.requests(accessToken) }.onFailure { error = it.message ?: "خطا" } } }) { Text(if (isEnglish) "Accept" else "پذیرش") }
                                        TextButton(onClick = { scope.launch { runCatching { client.updateRequest(accessToken, r.optString("id"), "declined"); requests = client.requests(accessToken) }.onFailure { error = it.message ?: "خطا" } } }) { Text(if (isEnglish) "Decline" else "رد") }
                                    }
                                }
                            }
                        }
                    }
                    if (requests.length() == 0 && !loading) Text(if (isEnglish) "No requests yet." else "درخواستی وجود ندارد.", color = muted)
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
        if (notice.isNotBlank()) Text(notice, color = Color(0xFF9BE7C4))
        if (error.isNotBlank()) Text(error, color = Color(0xFFFFB2A8))
    }
}
