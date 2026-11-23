package com.naeem.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LedgerEntry(val id: Long, val amountToman: Long, val category: String, val note: String, val isIncome: Boolean, val timestamp: Long)
data class ParsedTransaction(val amountToman: Long, val category: String, val note: String, val isIncome: Boolean)

class LedgerStore(context: Context) {
    private val prefs = context.getSharedPreferences("naeem_ai_ledger", Context.MODE_PRIVATE)

    fun entries(): List<LedgerEntry> {
        val raw = prefs.getString("entries_json", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                LedgerEntry(item.optLong("id"), item.optLong("amount"), item.optString("category", "متفرقه"), item.optString("note"), item.optBoolean("income"), item.optLong("timestamp"))
                    .takeIf { it.amountToman > 0L }
            }.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    fun add(amountToman: Long, category: String, note: String, isIncome: Boolean): LedgerEntry {
        require(amountToman > 0L) { "مبلغ باید بیشتر از صفر باشد." }
        val now = System.currentTimeMillis()
        val item = LedgerEntry(now, amountToman, category.ifBlank { "متفرقه" }, note, isIncome, now)
        save((entries() + item).take(5000))
        return item
    }

    fun delete(id: Long) = save(entries().filterNot { it.id == id })

    private fun save(items: List<LedgerEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("amount", item.amountToman).put("category", item.category)
                .put("note", item.note).put("income", item.isIncome).put("timestamp", item.timestamp))
        }
        prefs.edit().putString("entries_json", array.toString()).apply()
    }

    companion object {
        fun parseVoiceCommand(raw: String): ParsedTransaction? {
            val text = normalizeDigits(raw).replace('ي', 'ی').replace('ك', 'ک')
            val isIncome = Regex("(درآمد|واریز|دریافت کردم|حقوق گرفتم|فروختم)").containsMatchIn(text)
            val isExpense = Regex("(خرج|هزینه|پرداخت|خریدم|خرید کردم|پول دادم|هزینه کردم)").containsMatchIn(text)
            if (!isIncome && !isExpense) return null
            val match = Regex("""(\d[\d,٬.]*)(?:\s*(تومان|تومن|ریال|هزار|میلیون))?""").find(text) ?: return null
            val digits = match.groupValues[1].replace(",", "").replace("٬", "").replace(".", "")
            val value = digits.toLongOrNull() ?: return null
            val amount = when (match.groupValues.getOrElse(2) { "" }) {
                "هزار" -> value * 1_000L
                "میلیون" -> value * 1_000_000L
                "ریال" -> value / 10L
                else -> value
            }
            if (amount <= 0L) return null
            val category = when {
                Regex("(غذا|رستوران|ناهار|شام|کافه|سوپر|خواربار)").containsMatchIn(text) -> "خوراک"
                Regex("(بنزین|سوخت|تاکسی|اسنپ|مترو|ماشین|تعمیر خودرو)").containsMatchIn(text) -> "حمل‌ونقل"
                Regex("(اجاره|خانه|قبض|آب|برق|گاز|اینترنت)").containsMatchIn(text) -> "خانه و قبض"
                Regex("(لباس|کفش|خرید)").containsMatchIn(text) -> "خرید"
                Regex("(دکتر|دارو|درمان|بیمارستان)").containsMatchIn(text) -> "سلامت"
                else -> "متفرقه"
            }
            val note = text.replace(match.value, "").replace(Regex("(ثبت کن|یادداشت کن|بابت|برای|خرج کردم|هزینه کردم|پرداخت کردم|درآمد داشتم|دریافت کردم|خریدم|خرج|هزینه|درآمد|واریز)"), " ").trim()
            return ParsedTransaction(amount, if (isIncome) "درآمد" else category, note.ifBlank { if (isIncome) "دریافت وجه" else "ثبت از طریق مکالمه" }, isIncome)
        }

        private fun normalizeDigits(value: String): String {
            val persian = "۰۱۲۳۴۵۶۷۸۹"
            val arabic = "٠١٢٣٤٥٦٧٨٩"
            return buildString(value.length) {
                value.forEach { char ->
                    val p = persian.indexOf(char)
                    val a = arabic.indexOf(char)
                    append(when { p >= 0 -> ('0'.code + p).toChar(); a >= 0 -> ('0'.code + a).toChar(); else -> char })
                }
            }
        }
    }
}
