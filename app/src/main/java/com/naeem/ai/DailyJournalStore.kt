package com.naeem.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DailyJournalEntry(
    val id: String,
    val timestamp: Long,
    val category: String,
    val title: String,
    val detail: String
)

class DailyJournalStore(context: Context) {
    private val prefs = context.getSharedPreferences("naeem_daily_journal", Context.MODE_PRIVATE)

    fun entries(): List<DailyJournalEntry> = runCatching {
        val array = JSONArray(prefs.getString("entries", "[]"))
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { obj ->
                DailyJournalEntry(
                    obj.optString("id"), obj.optLong("timestamp"), obj.optString("category"),
                    obj.optString("title"), obj.optString("detail")
                )
            }
        }.sortedByDescending { it.timestamp }
    }.getOrDefault(emptyList())

    fun add(category: String, title: String, detail: String, timestamp: Long = System.currentTimeMillis()): DailyJournalEntry {
        val item = DailyJournalEntry(UUID.randomUUID().toString(), timestamp, category, title, detail)
        val all = (listOf(item) + entries()).take(3000)
        val array = JSONArray()
        all.forEach { row ->
            array.put(JSONObject().put("id", row.id).put("timestamp", row.timestamp)
                .put("category", row.category).put("title", row.title).put("detail", row.detail))
        }
        prefs.edit().putString("entries", array.toString()).apply()
        return item
    }

    fun captureNarrative(raw: String): List<DailyJournalEntry> {
        val normalized = raw.replace('ي', 'ی').replace('ك', 'ک')
            .replace(Regex("[۰-۹]")) { (it.value[0] - '۰').toString() }
        val parts = normalized.split(Regex("[،؛.\\n]+|\\bبعدش\\b|\\bبعد از آن\\b|\\bسپس\\b|\\bبعد\\b|\\s+و\\s+(?=(?:شب|عصر|صبح|ظهر))"))
            .map { it.trim().trim(',', '؛', '،', ' ') }.filter { it.length >= 5 }
        val captured = mutableListOf<DailyJournalEntry>()
        parts.forEach { part ->
            val lower = part.lowercase(Locale.ROOT)
            val isNarrative = listOf("رفتم", "کردم", "خوردم", "خریدم", "خوابیدم", "داشتم", "بودم", "پرداخت کردم", "انجام دادم", "تمرین کردم").any { lower.contains(it) }
            if (!isNarrative) return@forEach
            val category = when {
                listOf("باشگاه", "تمرین", "ورزش", "دوچرخه", "دوچرخه‌سواری", "دویدن", "پیاده‌روی", "کوهنوردی", "شنا").any { lower.contains(it) } -> "ورزش"
                listOf("خوردم", "صبحانه", "ناهار", "شام", "میان‌وعده", "غذا", "قهوه").any { lower.contains(it) } -> "تغذیه"
                listOf("خوابیدم", "خواب", "استراحت کردم", "چرت").any { lower.contains(it) } -> "خواب و استراحت"
                listOf("سر کار", "محل کار", "جلسه", "مشتری", "پروژه", "کار کردم", "اداری").any { lower.contains(it) } -> "کار"
                listOf("خریدم", "هزینه کردم", "پرداخت کردم", "خرج کردم", "تومان", "ریال").any { lower.contains(it) } -> "مالی و خرید"
                listOf("کوه", "طبیعت", "جنگل", "کمپ", "سفر", "گردش").any { lower.contains(it) } -> "سفر و طبیعت"
                else -> null
            }
            if (category != null) {
                val title = when (category) {
                    "ورزش" -> listOf("دوچرخه", "دویدن", "پیاده‌روی", "کوهنوردی", "شنا", "باشگاه").firstOrNull { lower.contains(it) } ?: "فعالیت ورزشی"
                    "تغذیه" -> listOf("صبحانه", "ناهار", "شام", "میان‌وعده").firstOrNull { lower.contains(it) } ?: "وعده غذایی"
                    "خواب و استراحت" -> "خواب و استراحت"
                    "کار" -> "فعالیت کاری"
                    "مالی و خرید" -> "خرید یا هزینه"
                    else -> "فعالیت سفر و طبیعت"
                }
                // Avoid creating a new journal row when the same sentence is repeated
                // by voice recognition or sent again within a short interval.
                val duplicate = entries().any {
                    it.detail.equals(part, ignoreCase = true) &&
                        System.currentTimeMillis() - it.timestamp in 0L..(15L * 60L * 1000L)
                }
                if (!duplicate) captured += add(category, title, part)
            }
        }
        return captured
    }

    fun remove(id: String) {
        val remaining = entries().filterNot { it.id == id }
        val array = JSONArray()
        remaining.forEach { row ->
            array.put(JSONObject().put("id", row.id).put("timestamp", row.timestamp)
                .put("category", row.category).put("title", row.title).put("detail", row.detail))
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }

    fun report(from: Long, to: Long): List<DailyJournalEntry> =
        entries().filter { it.timestamp in from until to }.sortedBy { it.timestamp }

    companion object {
        fun startOfDay(now: Long = System.currentTimeMillis()): Long =
            java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

        fun dateLabel(timestamp: Long): String =
            SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa", "IR")).format(Date(timestamp))
    }
}
