package com.naeem.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StoredChatMessage(val text: String, val fromUser: Boolean)
data class StoredConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?,
    val messages: List<StoredChatMessage>
)

class ConversationStore(context: Context) {
    private val prefs = context.getSharedPreferences("naeem_ai_conversations_v2", Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences("naeem_ai_conversation", Context.MODE_PRIVATE)

    init { migrateLegacyHistoryOnce() }

    @Synchronized
    fun list(includeArchived: Boolean = false): List<StoredConversation> =
        readAll().filter { includeArchived || it.archivedAt == null }.sortedByDescending { it.updatedAt }

    @Synchronized
    fun activeId(): String {
        val all = readAll()
        val stored = prefs.getString("active_id", null)
        if (stored != null && all.any { it.id == stored && it.archivedAt == null }) return stored
        val next = all.filter { it.archivedAt == null }.maxByOrNull { it.updatedAt }?.id
        if (next != null) {
            prefs.edit().putString("active_id", next).apply()
            return next
        }
        return create()
    }

    @Synchronized
    fun load(): List<StoredChatMessage> = load(activeId())

    @Synchronized
    fun load(conversationId: String): List<StoredChatMessage> =
        readAll().firstOrNull { it.id == conversationId }?.messages.orEmpty()

    @Synchronized
    fun save(messages: List<ChatMessage>) = save(activeId(), messages)

    @Synchronized
    fun save(conversationId: String, messages: List<ChatMessage>) {
        val all = readAll().toMutableList()
        val index = all.indexOfFirst { it.id == conversationId }
        if (index < 0) return
        val stored = messages.filter { it.image == null }.map { StoredChatMessage(it.text, it.fromUser) }
        val current = all[index]
        val firstUser = stored.firstOrNull { it.fromUser }?.text?.trim().orEmpty()
        val title = if (current.title == "گفت‌وگوی تازه" && firstUser.isNotBlank()) firstUser.take(38) else current.title
        all[index] = current.copy(title = title, updatedAt = System.currentTimeMillis(), messages = stored)
        persist(all)
    }

    @Synchronized
    fun create(title: String = "گفت‌وگوی تازه"): String {
        val now = System.currentTimeMillis()
        val item = StoredConversation(UUID.randomUUID().toString(), title.trim().ifBlank { "گفت‌وگوی تازه" }, now, now, null, emptyList())
        persist(readAll() + item)
        prefs.edit().putString("active_id", item.id).apply()
        return item.id
    }

    @Synchronized
    fun switchTo(conversationId: String): Boolean {
        val exists = readAll().any { it.id == conversationId && it.archivedAt == null }
        if (!exists) return false
        prefs.edit().putString("active_id", conversationId).apply()
        return true
    }

    @Synchronized
    fun rename(conversationId: String, title: String): Boolean {
        if (title.isBlank()) return false
        val all = readAll().toMutableList()
        val index = all.indexOfFirst { it.id == conversationId }
        if (index < 0) return false
        all[index] = all[index].copy(title = title.trim(), updatedAt = System.currentTimeMillis())
        persist(all)
        return true
    }

    @Synchronized
    fun archive(conversationId: String, archived: Boolean = true) {
        val all = readAll().toMutableList()
        val index = all.indexOfFirst { it.id == conversationId }
        if (index < 0) return
        all[index] = all[index].copy(archivedAt = if (archived) System.currentTimeMillis() else null)
        persist(all)
        if (archived && prefs.getString("active_id", null) == conversationId) {
            val next = all.filter { it.archivedAt == null }.maxByOrNull { it.updatedAt }?.id
            if (next == null) create() else prefs.edit().putString("active_id", next).apply()
        }
    }

    @Synchronized
    fun delete(conversationId: String) {
        val wasActive = prefs.getString("active_id", null) == conversationId
        val remaining = readAll().filterNot { it.id == conversationId }
        persist(remaining)
        if (wasActive) {
            val next = remaining.filter { it.archivedAt == null }.maxByOrNull { it.updatedAt }?.id
            if (next == null) create() else prefs.edit().putString("active_id", next).apply()
        }
    }

    @Synchronized
    fun search(query: String, includeArchived: Boolean = false): List<StoredConversation> {
        val q = query.trim()
        val source = list(includeArchived)
        if (q.isBlank()) return source
        return source.filter { thread ->
            thread.title.contains(q, ignoreCase = true) ||
                thread.messages.any { it.text.contains(q, ignoreCase = true) }
        }
    }

    @Synchronized
    fun clear() {
        val id = activeId()
        save(id, emptyList())
    }

    private fun migrateLegacyHistoryOnce() {
        if (prefs.contains("threads_json")) return
        val oldMessages = runCatching {
            val raw = legacyPrefs.getString("history", "[]") ?: "[]"
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val text = item.optString("text")
                if (text.isBlank()) null else StoredChatMessage(text, item.optBoolean("fromUser"))
            }
        }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val title = oldMessages.firstOrNull { it.fromUser }?.text?.take(38)?.ifBlank { "گفت‌وگوی قبلی" } ?: "گفت‌وگوی قبلی"
        persist(listOf(StoredConversation(id, title, now, now, null, oldMessages)))
        prefs.edit().putString("active_id", id).apply()
    }

    private fun readAll(): List<StoredConversation> = runCatching {
        val raw = prefs.getString("threads_json", "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val messagesJson = o.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until messagesJson.length()).mapNotNull { j ->
                val m = messagesJson.optJSONObject(j) ?: return@mapNotNull null
                val text = m.optString("text")
                if (text.isBlank()) null else StoredChatMessage(text, m.optBoolean("fromUser"))
            }
            StoredConversation(
                o.optString("id"), o.optString("title", "گفت‌وگوی تازه"),
                o.optLong("createdAt"), o.optLong("updatedAt"),
                if (o.isNull("archivedAt")) null else o.optLong("archivedAt"), messages
            )
        }
    }.getOrDefault(emptyList())

    private fun persist(items: List<StoredConversation>) {
        val array = JSONArray()
        items.forEach { thread ->
            val messages = JSONArray()
            thread.messages.forEach { messages.put(JSONObject().put("text", it.text).put("fromUser", it.fromUser)) }
            array.put(JSONObject().put("id", thread.id).put("title", thread.title)
                .put("createdAt", thread.createdAt).put("updatedAt", thread.updatedAt)
                .put("archivedAt", thread.archivedAt ?: JSONObject.NULL).put("messages", messages))
        }
        prefs.edit().putString("threads_json", array.toString()).commit()
    }
}
