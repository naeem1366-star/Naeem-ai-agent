package com.naeem.ai

import android.graphics.BitmapFactory
import android.util.Base64
import com.naeem.ai.core.database.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupSummary(
    val financeEntries: Int,
    val workouts: Int,
    val trips: Int,
    val memories: Int,
    val conversations: Int,
    val images: Int
)

/**
 * User-controlled, encrypted, portable backup. The user chooses the SAF destination;
 * no data is uploaded to a server. The passphrase is never persisted by this class.
 */
class NaeemBackupManager(
    private val ledger: LedgerStore,
    private val sports: SportStore,
    private val travel: TravelStore,
    private val assistantData: AssistantDataStore,
    private val conversations: ConversationStore
) {
    fun exportEncrypted(passphrase: String): String {
        require(passphrase.length >= 8) { "رمز پشتیبان باید حداقل ۸ نویسه داشته باشد." }
        val payload = buildPayload().toString().toByteArray(StandardCharsets.UTF_8)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(payload)
        return JSONObject()
            .put("format", "NAEEM_AI_BACKUP")
            .put("version", 1)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", 180000)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
    }

    fun restoreEncrypted(raw: String, passphrase: String): BackupSummary {
        require(passphrase.length >= 8) { "رمز پشتیبان باید حداقل ۸ نویسه داشته باشد." }
        val envelope = JSONObject(raw)
        require(envelope.optString("format") == "NAEEM_AI_BACKUP" && envelope.optInt("version") == 1) {
            "قالب فایل پشتیبان Naeem AI شناخته‌شده نیست."
        }
        val salt = Base64.decode(envelope.getString("salt"), Base64.DEFAULT)
        val iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT)
        val encrypted = Base64.decode(envelope.getString("data"), Base64.DEFAULT)
        val decrypted = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (_: Exception) {
            throw IllegalArgumentException("رمز پشتیبان اشتباه است یا فایل آسیب دیده است.")
        }
        val payload = JSONObject(String(decrypted, StandardCharsets.UTF_8))
        require(payload.optInt("schemaVersion") == 1) { "نسخهٔ اطلاعات داخل پشتیبان پشتیبانی نمی‌شود." }

        // Parse and validate every section before replacing any local data.
        val finance = parseFinance(payload.getJSONArray("finance"))
        val workouts = parseWorkouts(payload.getJSONArray("sports"))
        val trips = parseTrips(payload.getJSONArray("trips"))
        val memories = parseStrings(payload.getJSONArray("memories"))
        val threads = parseConversations(payload.getJSONArray("conversations"))
        val activeId = payload.optString("activeConversationId").takeIf { it.isNotBlank() }
        val images = parseImages(payload.getJSONArray("images"))

        ledger.replaceAll(finance)
        sports.save(workouts)
        travel.save(trips)
        assistantData.replaceMemories(memories)
        conversations.replaceAll(threads, activeId)
        assistantData.clearImages()
        images.forEach { assistantData.restoreImage(it.bytes, it.prompt, it.createdAt) }

        return BackupSummary(finance.size, workouts.size, trips.size, memories.size, threads.size, images.size)
    }

    private fun buildPayload(): JSONObject {
        val finance = JSONArray()
        ledger.entries().forEach { item ->
            finance.put(JSONObject().put("id", item.id).put("amount", item.amountToman)
                .put("category", item.category).put("note", item.note).put("income", item.isIncome).put("timestamp", item.timestamp))
        }
        val workouts = JSONArray()
        sports.load().forEach { item ->
            workouts.put(JSONObject().put("id", item.id).put("title", item.title).put("category", item.category)
                .put("date", item.date).put("minutes", item.minutes).put("distance", item.distance)
                .put("sets", item.sets).put("reps", item.reps).put("weight", item.weight).put("notes", item.notes))
        }
        val trips = JSONArray()
        travel.load().forEach { trip ->
            val members = JSONArray().also { a -> trip.members.forEach(a::put) }
            val expenses = JSONArray()
            trip.expenses.forEach { item ->
                val beneficiaries = JSONArray().also { a -> item.beneficiaries.forEach(a::put) }
                expenses.put(JSONObject().put("id", item.id).put("title", item.title).put("amount", item.amount).put("payer", item.payer).put("beneficiaries", beneficiaries))
            }
            trips.put(JSONObject().put("id", trip.id).put("title", trip.title).put("destination", trip.destination)
                .put("dates", trip.dates).put("members", members).put("expenses", expenses))
        }
        val memories = JSONArray().also { a -> assistantData.memories().forEach(a::put) }
        val threads = JSONArray()
        conversations.list(includeArchived = true).forEach { thread ->
            val messages = JSONArray()
            thread.messages.forEach { m -> messages.put(JSONObject().put("text", m.text).put("fromUser", m.fromUser)) }
            threads.put(JSONObject().put("id", thread.id).put("title", thread.title).put("createdAt", thread.createdAt)
                .put("updatedAt", thread.updatedAt).put("archivedAt", thread.archivedAt ?: JSONObject.NULL).put("messages", messages))
        }
        val images = JSONArray()
        assistantData.images().forEach { item ->
            images.put(JSONObject().put("prompt", item.prompt).put("createdAt", item.createdAt)
                .put("bytes", Base64.encodeToString(item.file.readBytes(), Base64.NO_WRAP)))
        }
        return JSONObject().put("schemaVersion", 1).put("exportedAt", System.currentTimeMillis())
            .put("activeConversationId", conversations.activeId())
            .put("finance", finance).put("sports", workouts).put("trips", trips)
            .put("memories", memories).put("conversations", threads).put("images", images)
    }

    private fun parseFinance(array: JSONArray): List<LedgerEntry> = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        val amount = o.getLong("amount")
        require(amount > 0L) { "مبلغ یکی از تراکنش‌های پشتیبان نامعتبر است." }
        LedgerEntry(o.getLong("id"), amount, o.optString("category"), o.optString("note"), o.optBoolean("income"), o.getLong("timestamp"))
    }.also { require(it.map { row -> row.id }.distinct().size == it.size) { "شناسهٔ تراکنش تکراری است." } }

    private fun parseWorkouts(array: JSONArray): List<SportLog> = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        val title = o.optString("title")
        val minutes = o.optInt("minutes")
        val distance = o.optDouble("distance")
        require(title.isNotBlank() && minutes >= 0 && distance >= 0.0) { "یکی از تمرین‌های پشتیبان نامعتبر است." }
        SportLog(o.getString("id"), title, o.optString("category"), o.getLong("date"), minutes, distance,
            o.optInt("sets"), o.optInt("reps"), o.optDouble("weight"), o.optString("notes"))
    }

    private fun parseTrips(array: JSONArray): List<LocalTrip> = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        val membersArray = o.getJSONArray("members")
        val expensesArray = o.getJSONArray("expenses")
        val members = (0 until membersArray.length()).map { membersArray.getString(it) }
        val expenses = (0 until expensesArray.length()).map { j ->
            val e = expensesArray.getJSONObject(j)
            val amount = e.getLong("amount")
            require(amount > 0L) { "مبلغ یکی از هزینه‌های سفر نامعتبر است." }
            val beneficiariesArray = e.optJSONArray("beneficiaries")
            val beneficiaries = if (beneficiariesArray == null) members else (0 until beneficiariesArray.length()).map { beneficiariesArray.getString(it) }
            TripExpense(e.getString("id"), e.getString("title"), amount, e.getString("payer"), beneficiaries)
        }
        val title = o.optString("title")
        val destination = o.optString("destination")
        require(title.isNotBlank() && destination.isNotBlank()) { "عنوان یا مقصد یکی از سفرها خالی است." }
        LocalTrip(o.getString("id"), title, destination, o.optString("dates"), members, expenses)
    }

    private fun parseStrings(array: JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }.filter { it.isNotBlank() }

    private fun parseConversations(array: JSONArray): List<StoredConversation> = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        val messagesArray = o.getJSONArray("messages")
        val messages = (0 until messagesArray.length()).map { j ->
            val m = messagesArray.getJSONObject(j)
            StoredChatMessage(m.getString("text"), m.getBoolean("fromUser"))
        }
        StoredConversation(o.getString("id"), o.getString("title"), o.getLong("createdAt"), o.getLong("updatedAt"),
            if (o.isNull("archivedAt")) null else o.getLong("archivedAt"), messages)
    }

    private data class ImageBackup(val prompt: String, val createdAt: Long, val bytes: ByteArray)

    private fun parseImages(array: JSONArray): List<ImageBackup> = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        val bytes = Base64.decode(o.getString("bytes"), Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("یکی از تصاویر پشتیبان قابل خواندن نیست.")
        bitmap.recycle()
        ImageBackup(o.optString("prompt"), o.getLong("createdAt"), bytes)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 180000, 256)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
