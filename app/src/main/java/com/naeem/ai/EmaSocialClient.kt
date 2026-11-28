package com.naeem.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class EmaSocialProfile(
    val displayName: String = "", val headline: String = "", val bio: String = "", val city: String = "",
    val interests: List<String> = emptyList(), val skills: List<String> = emptyList(), val goals: List<String> = emptyList(),
    val experienceSummary: String = "", val collaborationModes: List<String> = emptyList(),
    val availability: String = "not_set", val visibility: String = "private",
    val discoverable: Boolean = false, val aiMatchingConsent: Boolean = false,
    val profileProcessingConsent: Boolean = false, val semanticMatchingConsent: Boolean = false
)
data class EmaSocialPerson(
    val userId: String, val displayName: String, val headline: String, val bio: String, val city: String,
    val interests: List<String>, val skills: List<String>, val goals: List<String>,
    val score: Int = 0, val matchType: String = "", val reasons: List<String> = emptyList()
)
data class EmaSocialControl(val enabled: Boolean, val maintenanceMessage: String, val updatedAt: String)

class EmaSocialClient {
    private val root = EmaSupabaseConfig.URL.trimEnd('/') + "/functions/v1/ema-social"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(path: String, token: String, method: String = "GET", body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            require(token.isNotBlank()) { "برای استفاده از شبکه اجتماعی ابتدا وارد حساب شو." }
            val builder = Request.Builder().url(root + path)
                .header("apikey", EmaSupabaseConfig.PUBLISHABLE_KEY)
                .header("Authorization", "Bearer " + token).header("Accept", "application/json")
            when (method) {
                "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(jsonType))
                "PUT" -> builder.put((body ?: JSONObject()).toString().toRequestBody(jsonType))
                "PATCH" -> builder.patch((body ?: JSONObject()).toString().toRequestBody(jsonType))
                else -> builder.get()
            }
            http.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
                if (!response.isSuccessful) {
                    val error = parsed.optJSONObject("error")
                    throw IllegalStateException(error?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: error?.optString("code")?.takeIf { it.isNotBlank() }
                        ?: "ارتباط با شبکه اجتماعی برقرار نشد (HTTP ${response.code}).")
                }
                parsed
            }
        }

    private fun strings(array: JSONArray?): List<String> =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optString(it)?.takeIf(String::isNotBlank) }

    private fun profileFrom(o: JSONObject): EmaSocialProfile = EmaSocialProfile(
        o.optString("display_name"), o.optString("headline"), o.optString("bio"), o.optString("city"),
        strings(o.optJSONArray("interests")), strings(o.optJSONArray("skills")), strings(o.optJSONArray("goals")),
        o.optString("experience_summary"), strings(o.optJSONArray("collaboration_modes")),
        o.optString("availability", "not_set"), o.optString("visibility", "private"),
        o.optBoolean("discoverable"), o.optBoolean("ai_matching_consent")
    )

    private fun people(json: JSONObject, key: String): List<EmaSocialPerson> {
        val array = json.optJSONArray(key) ?: JSONArray()
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { o ->
            EmaSocialPerson(o.optString("user_id"), o.optString("display_name"), o.optString("headline"),
                o.optString("bio"), o.optString("city"), strings(o.optJSONArray("interests")),
                strings(o.optJSONArray("skills")), strings(o.optJSONArray("goals")),
                o.optInt("score"), o.optString("matchType"), strings(o.optJSONArray("reasons")))
        } }
    }

    suspend fun status(token: String): EmaSocialControl {
        val o = call("/v1/social/status", token)
        return EmaSocialControl(o.optBoolean("enabled"), o.optString("maintenanceMessage"), o.optString("checkedAt"))
    }
    suspend fun profile(token: String): EmaSocialProfile? {
        val response = call("/v1/social/profile", token)
        val o = response.optJSONObject("profile") ?: return null
        val consent = response.optJSONObject("consent") ?: JSONObject()
        return profileFrom(o).copy(profileProcessingConsent = consent.optBoolean("profile_processing"), semanticMatchingConsent = consent.optBoolean("semantic_matching"))
    }
    suspend fun saveProfile(token: String, p: EmaSocialProfile, profileConsent: Boolean, semanticConsent: Boolean) {
        val body = JSONObject()
            .put("displayName", p.displayName).put("headline", p.headline).put("bio", p.bio).put("city", p.city)
            .put("interests", JSONArray(p.interests)).put("skills", JSONArray(p.skills)).put("goals", JSONArray(p.goals))
            .put("experienceSummary", p.experienceSummary).put("collaborationModes", JSONArray(p.collaborationModes))
            .put("availability", p.availability).put("visibility", p.visibility)
            .put("discoverable", p.discoverable).put("aiMatchingConsent", p.aiMatchingConsent)
            .put("profileProcessingConsent", profileConsent).put("semanticMatchingConsent", semanticConsent)
        call("/v1/social/profile", token, "PUT", body)
    }
    suspend fun discover(token: String): List<EmaSocialPerson> = people(call("/v1/social/discover", token), "people")
    suspend fun matches(token: String): List<EmaSocialPerson> = people(call("/v1/social/matches", token), "matches")
    suspend fun requests(token: String): JSONArray = call("/v1/social/connections/requests", token).optJSONArray("requests") ?: JSONArray()
    suspend fun sendRequest(token: String, recipientId: String, message: String) {
        call("/v1/social/connections/requests", token, "POST",
            JSONObject().put("recipientUserId", recipientId).put("message", message))
    }
    suspend fun updateRequest(token: String, id: String, decision: String) {
        call("/v1/social/connections/requests/$id", token, "PATCH", JSONObject().put("decision", decision))
    }
    suspend fun adminControl(token: String): EmaSocialControl {
        val o = call("/v1/social/admin/control", token).optJSONObject("control") ?: JSONObject()
        return EmaSocialControl(o.optBoolean("enabled"), o.optString("maintenance_message"), o.optString("updated_at"))
    }
    suspend fun setAdminControl(token: String, enabled: Boolean, message: String): EmaSocialControl {
        val o = call("/v1/social/admin/control", token, "PATCH",
            JSONObject().put("enabled", enabled).put("maintenanceMessage", message))
            .optJSONObject("control") ?: JSONObject()
        return EmaSocialControl(o.optBoolean("enabled"), o.optString("maintenance_message"), o.optString("updated_at"))
    }
}
