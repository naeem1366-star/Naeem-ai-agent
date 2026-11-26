package com.naeem.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SupabaseAuthScreen(auth: SupabaseAuthClient, onAuthenticated: (SupabaseSession) -> Unit) {
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isSignup = mode == "signup"
    val isRecovery = mode == "recovery"

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.bg_mountain),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.58f }.blur(5.dp),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color(0x77050B16)))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SupraGlassPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), radius = 28.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painter = painterResource(R.drawable.ic_brain_mark), contentDescription = "لوگوی ایما",
                modifier = Modifier.size(104.dp), contentScale = ContentScale.Fit)
            Text("Ima by Naeem Dehghani", color = Color(0xFFB9C5D5), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(when (mode) { "signup" -> "ساخت حساب کاربری"; "recovery" -> "بازیابی رمز عبور"; else -> "ورود به حساب" },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("با ایمیل و رمز عبور وارد فضای شخصی خودت شو", color = Color(0xFFB9C5D5), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            val authFieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color(0xFFF1F4F8),
                focusedContainerColor = Color(0x1AFFFFFF), unfocusedContainerColor = Color(0x12FFFFFF),
                focusedBorderColor = Color(0xFFB7D2FF), unfocusedBorderColor = Color(0xFF71839D),
                focusedLabelColor = Color(0xFFB7D2FF), unfocusedLabelColor = Color(0xFFCAD4E2),
                focusedPlaceholderColor = Color(0xFF9EADC2), unfocusedPlaceholderColor = Color(0xFF9EADC2),
                cursorColor = Color(0xFFB7D2FF)
            )
            OutlinedTextField(value = email, onValueChange = { email = it; message = "" }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("ایمیل") }, placeholder = { Text("name@example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(16.dp), colors = authFieldColors)
            if (!isRecovery) OutlinedTextField(value = password, onValueChange = { password = it; message = "" },
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("رمز عبور") },
                visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp), colors = authFieldColors)
            if (isSignup) OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it; message = "" },
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("تکرار رمز عبور") },
                visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp), colors = authFieldColors)
            if (message.isNotBlank()) Text(message, color = if (isError) Color(0xFFFF9C9C) else Color(0xFF9ED8B0), fontSize = 13.sp)
            Button(onClick = {
                if (busy) return@Button
                val cleanEmail = email.trim()
                when {
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> { message = "ایمیل معتبر وارد کن."; isError = true }
                    isRecovery -> {
                        busy = true
                        scope.launch {
                            try { auth.sendPasswordReset(cleanEmail); message = "اگر این ایمیل ثبت شده باشد، لینک بازیابی برایش ارسال می‌شود."; isError = false }
                            catch (e: Exception) { message = e.message ?: "ارسال لینک بازیابی انجام نشد."; isError = true }
                            finally { busy = false }
                        }
                    }
                    isSignup && password.length < 8 -> { message = "رمز عبور باید حداقل ۸ نویسه داشته باشد."; isError = true }
                    isSignup && password != confirmPassword -> { message = "تکرار رمز عبور با رمز اصلی یکسان نیست."; isError = true }
                    else -> {
                        busy = true
                        scope.launch {
                            try {
                                val result = if (isSignup) auth.signUp(cleanEmail, password) else auth.signIn(cleanEmail, password)
                                if (result.session != null) onAuthenticated(result.session)
                                else {
                                    mode = "login"; password = ""; confirmPassword = ""
                                    message = "حساب ساخته شد. ایمیلت را تأیید کن و بعد وارد شو."; isError = false
                                }
                            } catch (e: Exception) { message = e.message ?: "ارتباط با سرویس ورود برقرار نشد."; isError = true }
                            finally { busy = false }
                        }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SupraGlass.Ice, contentColor = Color(0xFF071426))) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF101B2B), strokeWidth = 2.dp)
                else Text(when (mode) { "signup" -> "ساخت حساب"; "recovery" -> "ارسال لینک بازیابی"; else -> "ورود" }, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (mode != "login") TextButton(onClick = { mode = "login"; message = ""; password = ""; confirmPassword = "" }) { Text("بازگشت به ورود") }
                else {
                    TextButton(onClick = { mode = "signup"; message = "" }) { Text("ساخت حساب جدید") }
                    Text("•", color = Color(0xFF9EADC2))
                    TextButton(onClick = { mode = "recovery"; message = "" }) { Text("فراموشی رمز") }
                }
            }
        }
            }
        }
    }
}
