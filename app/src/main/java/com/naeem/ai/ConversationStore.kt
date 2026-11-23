package com.naeem.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredChatMessage(val text: String, val fromUser: Boolean)

class ConversationStore(context: Context) {
    private val prefs = context.getSharedPreferences("naeem_ai_conversation", Context.MODE_PRIVATE)

    fun load(): List<StoredChatMessage> {
        val raw = prefs.getString("history", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val text = item.optString("text")
                if (text.isBlank()) null else StoredChatMessage(text, item.optBoolean("fromUser"))
            }
        }.getOrDefault(emptyList())
    }

    fun save(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.filter { it.image == null }.forEach { message ->
            array.put(JSONObject().put("text", message.text).put("fromUser", message.fromUser))
        }
        prefs.edit().putString("history", array.toString()).commit()
    }

    fun clear() = prefs.edit().remove("history").apply()
}
