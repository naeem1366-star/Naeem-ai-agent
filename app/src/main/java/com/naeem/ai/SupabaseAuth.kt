package com.naeem.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ConnectionSpec
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object EmaSupabaseConfig {
    const val URL = "https://ifloyzpvgjgmneqgcggu.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_Vq7VJtBwR8azcEKsIOmc-Q_aqO-fzSh"
}

data class SupabaseSession(val userId: String, val email: String, val accessToken: String, val refreshToken: String, val expiresAt: Long)
data class SupabaseAuthResult(val session: SupabaseSession? = null, val needsEmailConfirmation: Boolean = false)

class SupabaseAuthClient(context: Context) {
    // EmaSupabaseConfig contains only the public client URL and publishable key.
    private val http = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // Some mobile VPN/proxy routes reset HTTP/2 streams; force broadly compatible HTTP/1.1.
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    // Avoid a launch crash if Android Keystore is unavailable on a device.
    private val preferences: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context, "supabase_auth_session",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences("supabase_auth_session_fallback", Context.MODE_PRIVATE)
    }

    suspend fun signIn(email: String, password: String): SupabaseAuthResult = withContext(Dispatchers.IO) {
        val data = post("/auth/v1/token?grant_type=password", JSONObject().put("email", email.trim()).put("password", password))
        val session = parseSession(data) ?: throw IOException("ورود انجام شد اما نشست کاربر از سرور دریافت نشد.")
        save(session)
        SupabaseAuthResult(session = session)
    }

    suspend fun signInAnonymously(): SupabaseAuthResult = withContext(Dispatchers.IO) {
        // Supabase anonymous sign-in creates a real short-lived Auth session, allowing
        // the secure gateway to apply its normal per-user quota without collecting an email.
        val data = post("/auth/v1/signup", JSONObject())
        val session = parseSession(data)
            ?: throw IOException("ورود مهمان انجام نشد؛ سرویس ورود مهمان در تنظیمات سرور فعال نیست.")
        save(session)
        SupabaseAuthResult(session = session)
    }

    suspend fun signUp(email: String, password: String): SupabaseAuthResult = withContext(Dispatchers.IO) {
        val data = post("/auth/v1/signup", JSONObject().put("email", email.trim()).put("password", password))
        val session = parseSession(data)
        if (session != null) save(session)
        SupabaseAuthResult(session = session, needsEmailConfirmation = session == null)
    }

    suspend fun sendPasswordReset(email: String) = withContext(Dispatchers.IO) {
        post("/auth/v1/recover", JSONObject().put("email", email.trim()))
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = readSavedSession()
        if (session != null) runCatching { post("/auth/v1/logout", JSONObject(), session.accessToken) }
        clear()
    }

    suspend fun restoreSession(): SupabaseSession? = withContext(Dispatchers.IO) {
        val saved = readSavedSession() ?: return@withContext null
        if (saved.expiresAt > System.currentTimeMillis() / 1000 + 90) return@withContext saved
        if (saved.refreshToken.isBlank()) { clear(); return@withContext null }
        try {
            val data = post("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", saved.refreshToken))
            val refreshed = parseSession(data) ?: throw IOException("نشست قابل تمدید نیست.")
            save(refreshed)
            refreshed
        } catch (_: Exception) {
            clear()
            null
        }
    }

    private fun post(path: String, body: JSONObject, accessToken: String? = null): JSONObject {
        val builder = Request.Builder().url(EmaSupabaseConfig.URL + path)
            // Publishable keys are API keys, not JWTs. Keep them only in the apikey header.
            .header("apikey", EmaSupabaseConfig.PUBLISHABLE_KEY)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonType))
        if (!accessToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer " + accessToken)
        }
        http.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val serverMessage = parsed.optString("msg").ifBlank { parsed.optString("message") }
                    .ifBlank { parsed.optString("error_description") }.ifBlank { parsed.optString("error") }
                val message = if (serverMessage.contains("invalid api key", ignoreCase = true) ||
                    serverMessage.contains("invalid_api_key", ignoreCase = true)) {
                    "کلید اتصال سرویس حساب کاربری معتبر نیست. باید کلید Publishable همین پروژه در تنظیمات Supabase بررسی و در نسخه برنامه جایگزین شود."
                } else {
                    serverMessage.ifBlank { "خطای ارتباط با سرویس ورود (HTTP " + response.code + ")" }
                }
                throw IOException(message)
            }
            return parsed
        }
    }

    private fun parseSession(data: JSONObject): SupabaseSession? {
        val access = data.optString("access_token")
        val refresh = data.optString("refresh_token")
        val user = data.optJSONObject("user") ?: return null
        val id = user.optString("id")
        val email = user.optString("email")
        if (access.isBlank() || refresh.isBlank() || id.isBlank()) return null
        val now = System.currentTimeMillis() / 1000
        val expiresAt = data.optLong("expires_at", now + data.optLong("expires_in", 3600))
        return SupabaseSession(id, email, access, refresh, expiresAt)
    }

    private fun save(session: SupabaseSession) {
        preferences.edit().putString("user_id", session.userId).putString("email", session.email)
            .putString("access_token", session.accessToken).putString("refresh_token", session.refreshToken)
            .putLong("expires_at", session.expiresAt).apply()
    }

    private fun readSavedSession(): SupabaseSession? {
        val id = preferences.getString("user_id", null) ?: return null
        val access = preferences.getString("access_token", null) ?: return null
        return SupabaseSession(id, preferences.getString("email", "").orEmpty(), access,
            preferences.getString("refresh_token", "").orEmpty(), preferences.getLong("expires_at", 0L))
    }

    private fun clear() { preferences.edit().clear().apply() }
}
