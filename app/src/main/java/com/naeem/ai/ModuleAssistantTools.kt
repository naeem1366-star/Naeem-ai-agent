package com.naeem.ai

import android.content.Context
import com.naeem.ai.core.assistant.*
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

sealed interface AssistantIntentResolution {
    data object NoMatch : AssistantIntentResolution
    data class Clarify(val question: String) : AssistantIntentResolution
    data class Ready(val action: AssistantAction) : AssistantIntentResolution
}

object PersianAssistantIntentResolver {
    private val amountPattern = Regex("""(\d[\d,٬.]*)\s*(میلیون|هزار|تومان|تومن|ریال)?""")

    fun resolve(raw: String, trips: List<LocalTrip> = emptyList()): AssistantIntentResolution {
        val text = normalize(raw)
        if (text.isBlank()) return AssistantIntentResolution.NoMatch

        if (listOf("یادت باشه", "به خاطر بسپار", "این رو ذخیره کن", "این را ذخیره کن").any { text.contains(it) }) {
            val note = text.replace("یادت باشه", "").replace("به خاطر بسپار", "")
                .replace("این رو ذخیره کن", "").replace("این را ذخیره کن", "").trim()
            return if (note.isBlank()) AssistantIntentResolution.Clarify("دقیقاً چه نکته‌ای را در حافظه ذخیره کنم؟")
            else ready(AssistantModule.MEMORY, "add_note", ActionAccess.WRITE, mapOf("note" to note))
        }
        if (text.contains("حافظه") && listOf("چی", "لیست", "یادداشت").any { text.contains(it) })
            return ready(AssistantModule.MEMORY, "list", ActionAccess.READ)

        if (text.contains("سفر") && listOf("هزینه", "خرج", "پرداخت").any { text.contains(it) }) {
            val amount = parseAmount(text) ?: return AssistantIntentResolution.Clarify("مبلغ هزینهٔ سفر را با واحد مشخص بگو؛ مثلاً «۲۵۰ هزار تومان».")
            val matches = trips.filter { text.contains(normalize(it.title)) || text.contains(normalize(it.destination)) }
            val trip = when {
                matches.size == 1 -> matches.first()
                matches.isEmpty() && trips.size == 1 -> trips.first()
                matches.isEmpty() -> return AssistantIntentResolution.Clarify("این هزینه مربوط به کدام سفر است؟ نام سفر یا مقصد را بگو.")
                else -> return AssistantIntentResolution.Clarify("چند سفر با این توضیح جور درمی‌آید؛ نام دقیق سفر را مشخص کن.")
            }
            val title = when {
                text.contains("غذا") || text.contains("رستوران") -> "خوراک"
                text.contains("بنزین") || text.contains("سوخت") -> "سوخت"
                text.contains("هتل") || text.contains("اقامت") -> "اقامت"
                else -> "هزینه ثبت‌شده از دستیار"
            }
            val payer = trip.members.firstOrNull { text.contains(normalize(it)) }
                ?: if (trip.members.size == 1) trip.members.first() else ""
            if (payer.isBlank()) return AssistantIntentResolution.Clarify("چه کسی این هزینه را پرداخت کرده؟ نام یکی از هم‌سفرها را بگو.")
            return ready(AssistantModule.TRAVEL, "add_expense", ActionAccess.WRITE,
                mapOf("trip_id" to trip.id, "amount" to amount.toString(), "title" to title, "payer" to payer))
        }
        if (text.contains("سفر") && listOf("لیست", "سفرهام", "سفرهای من").any { text.contains(it) })
            return ready(AssistantModule.TRAVEL, "list", ActionAccess.READ)

        val isWorkout = listOf("تمرین", "ورزش", "دوچرخه", "دوچرخه‌سواری", "دویدن", "پیاده‌روی", "شنا", "کوهنوردی", "یوگا").any { text.contains(it) }
        if (isWorkout && listOf("کردم", "رفتم", "ثبت", "امروز", "دیروز").any { text.contains(it) }) {
            val strength = listOf("وزنه", "بدنسازی", "اسکوات", "پرس").any { text.contains(it) }
            val minutes = Regex("""(\d+)\s*(دقیقه|min|minutes)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val distance = Regex("""(\d+(?:\.\d+)?)\s*(کیلومتر|km)""").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
            val title = when {
                text.contains("دوچرخه") -> "دوچرخه‌سواری"
                text.contains("دویدن") -> "دویدن"
                text.contains("پیاده") -> "پیاده‌روی"
                text.contains("شنا") -> "شنا"
                text.contains("کوهنوردی") -> "کوهنوردی"
                text.contains("یوگا") -> "یوگا"
                text.contains("اسکوات") -> "اسکوات"
                text.contains("پرس") -> "پرس"
                else -> "تمرین"
            }
            if (!strength && minutes == null)
                return AssistantIntentResolution.Clarify("مدت تمرین چند دقیقه بود؟ برای مثال «دوچرخه‌سواری ۲۵ کیلومتر در ۷۰ دقیقه».")
            val category = when {
                strength -> "قدرتی"
                title == "یوگا" -> "انعطاف"
                title == "کوهنوردی" -> "طبیعت‌گردی"
                else -> "هوازی"
            }
            return ready(AssistantModule.SPORTS, "add_workout", ActionAccess.WRITE, mapOf(
                "title" to title, "category" to category, "minutes" to (minutes ?: 0).toString(),
                "distance" to (distance ?: 0.0).toString(), "sets" to "0", "reps" to "0", "weight" to "0", "notes" to ""
            ))
        }
        if (text.contains("تمرین") || text.contains("ورزش")) {
            if (listOf("هفته", "پیشرفت", "آمار", "تاریخچه").any { text.contains(it) })
                return ready(AssistantModule.SPORTS, "summary", ActionAccess.READ)
        }

        val income = listOf("درآمد", "واریز", "حقوق گرفتم", "دریافت کردم", "فروختم").any { text.contains(it) }
        val expense = listOf("خرج کردم", "هزینه کردم", "پرداخت کردم", "خریدم", "پول دادم", "هزینه").any { text.contains(it) }
        if (income || expense) {
            val amount = parseAmount(text) ?: return AssistantIntentResolution.Clarify("مبلغ را همراه واحد مشخص کن؛ مثلاً «۲۰۰ هزار تومان خرج غذا کردم».")
            val category = if (income) "درآمد" else when {
                listOf("غذا", "رستوران", "ناهار", "شام", "کافه", "سوپر").any { text.contains(it) } -> "خوراک"
                listOf("بنزین", "سوخت", "تاکسی", "اسنپ", "مترو", "ماشین").any { text.contains(it) } -> "حمل‌ونقل"
                listOf("اجاره", "خانه", "قبض", "آب", "برق", "گاز", "اینترنت").any { text.contains(it) } -> "خانه و قبض"
                listOf("لباس", "کفش").any { text.contains(it) } -> "خرید"
                listOf("دکتر", "دارو", "درمان").any { text.contains(it) } -> "سلامت"
                else -> "متفرقه"
            }
            val note = when {
                text.contains("غذا") || text.contains("رستوران") || text.contains("کافه") -> "خوراک"
                text.contains("بنزین") -> "بنزین"
                text.contains("اجاره") -> "اجاره"
                income -> "دریافت وجه"
                else -> "ثبت از طریق مکالمه"
            }
            return ready(AssistantModule.FINANCE, if (income) "add_income" else "add_expense", ActionAccess.WRITE,
                mapOf("amount" to amount.toString(), "category" to category, "note" to note))
        }
        if (listOf("دخل و خرج", "مانده این ماه", "هزینه این ماه", "درآمد این ماه", "چقدر خرج").any { text.contains(it) })
            return ready(AssistantModule.FINANCE, "monthly_summary", ActionAccess.READ)
        if (listOf("آخرین تراکنش", "تراکنش‌های اخیر", "تراکنش های اخیر").any { text.contains(it) })
            return ready(AssistantModule.FINANCE, "recent", ActionAccess.READ)

        return AssistantIntentResolution.NoMatch
    }

    private fun parseAmount(text: String): Long? {
        val match = amountPattern.find(text) ?: return null
        val unit = match.groupValues[2]
        if (unit.isBlank()) return null
        val value = match.groupValues[1].replace(",", "").replace("٬", "").replace(".", "").toLongOrNull() ?: return null
        val amount = when (unit) {
            "هزار" -> value * 1_000L
            "میلیون" -> value * 1_000_000L
            "ریال" -> value / 10L
            else -> value
        }
        return amount.takeIf { it > 0L }
    }

    private fun normalize(value: String): String = value.replace('ي', 'ی').replace('ك', 'ک')
        .map { c -> when (c) {
            in '۰'..'۹' -> ('0'.code + (c - '۰')).toChar()
            in '٠'..'٩' -> ('0'.code + (c - '٠')).toChar()
            else -> c
        } }.joinToString("").lowercase(Locale.ROOT)

    private fun ready(module: AssistantModule, operation: String, access: ActionAccess, parameters: Map<String, String> = emptyMap()) =
        AssistantIntentResolution.Ready(AssistantAction(UUID.randomUUID().toString(), module, operation, access, parameters))
}

class FinanceAssistantTool(private val store: LedgerStore) : AssistantTool {
    override val module = AssistantModule.FINANCE
    override suspend fun validate(action: AssistantAction): ActionValidation = when (action.operation) {
        "monthly_summary", "recent" -> ActionValidation.Valid
        "add_expense", "add_income" -> if ((action.parameters["amount"]?.toLongOrNull() ?: 0L) > 0L) ActionValidation.Valid
            else ActionValidation.NeedsClarification("مبلغ معتبر را به تومان مشخص کن.")
        else -> ActionValidation.Invalid("این عملیات مالی پشتیبانی نمی‌شود.")
    }
    override suspend fun execute(action: AssistantAction): AssistantToolResult {
        val number = NumberFormat.getNumberInstance(Locale("fa", "IR"))
        return when (action.operation) {
            "monthly_summary" -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val rows = store.entries().filter { it.timestamp >= start }
                val income = rows.filter { it.isIncome }.sumOf { it.amountToman }
                val expense = rows.filterNot { it.isIncome }.sumOf { it.amountToman }
                AssistantToolResult(true, "خلاصه مالی این ماه:\nدرآمد: " + number.format(income) + " تومان\nهزینه: " + number.format(expense) + " تومان\nمانده: " + number.format(income - expense) + " تومان")
            }
            "recent" -> {
                val rows = store.entries().take(5)
                AssistantToolResult(true, if (rows.isEmpty()) "هنوز تراکنشی ثبت نشده." else rows.joinToString("\n") { "• " + it.note.ifBlank { it.category } + ": " + number.format(it.amountToman) + " تومان" })
            }
            "add_expense", "add_income" -> {
                val income = action.operation == "add_income"
                val item = store.add(action.parameters.getValue("amount").toLong(), action.parameters["category"].orEmpty(), action.parameters["note"].orEmpty(), income)
                AssistantToolResult(true, (if (income) "درآمد" else "هزینه") + "ٔ " + number.format(item.amountToman) + " تومان ثبت شد؛ دسته‌بندی: " + item.category + ".")
            }
            else -> AssistantToolResult(false, "عملیات مالی ناشناخته است.")
        }
    }
}

class SportsAssistantTool(context: Context) : AssistantTool {
    override val module = AssistantModule.SPORTS
    private val store = SportStore(context)
    override suspend fun validate(action: AssistantAction): ActionValidation = when (action.operation) {
        "summary", "history" -> ActionValidation.Valid
        "add_workout" -> if (action.parameters["title"].isNullOrBlank()) ActionValidation.NeedsClarification("نام فعالیت یا حرکت ورزشی چیست؟") else ActionValidation.Valid
        else -> ActionValidation.Invalid("این عملیات ورزشی پشتیبانی نمی‌شود.")
    }
    override suspend fun execute(action: AssistantAction): AssistantToolResult {
        val logs = store.load()
        val number = NumberFormat.getNumberInstance(Locale("fa", "IR"))
        return when (action.operation) {
            "summary" -> {
                val week = logs.filter { it.date >= System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L }
                AssistantToolResult(true, "آمار هفت روز اخیر:\nجلسه‌ها: " + week.size + "\nزمان تمرین: " + week.sumOf { it.minutes } + " دقیقه\nمسافت: " + number.format(week.sumOf { it.distance }) + " کیلومتر")
            }
            "history" -> AssistantToolResult(true, if (logs.isEmpty()) "هنوز تمرینی ثبت نشده." else logs.take(5).joinToString("\n") { "• " + it.title + "، " + it.minutes + " دقیقه، " + it.distance + " کیلومتر" })
            "add_workout" -> {
                val p = action.parameters
                val log = SportLog(UUID.randomUUID().toString(), p.getValue("title"), p["category"].orEmpty(), System.currentTimeMillis(),
                    p["minutes"]?.toIntOrNull() ?: 0, p["distance"]?.toDoubleOrNull() ?: 0.0,
                    p["sets"]?.toIntOrNull() ?: 0, p["reps"]?.toIntOrNull() ?: 0, p["weight"]?.toDoubleOrNull() ?: 0.0, p["notes"].orEmpty())
                store.save(listOf(log) + logs)
                AssistantToolResult(true, "تمرین «" + log.title + "» ثبت شد؛ " + log.minutes + " دقیقه، " + log.distance + " کیلومتر.")
            }
            else -> AssistantToolResult(false, "عملیات ورزشی ناشناخته است.")
        }
    }
}

class TravelAssistantTool(context: Context) : AssistantTool {
    override val module = AssistantModule.TRAVEL
    private val store = TravelStore(context)
    override suspend fun validate(action: AssistantAction): ActionValidation = when (action.operation) {
        "list" -> ActionValidation.Valid
        "add_expense" -> {
            val trip = store.load().firstOrNull { it.id == action.parameters["trip_id"] }
            when {
                trip == null -> ActionValidation.NeedsClarification("سفر موردنظر پیدا نشد؛ نام سفر را دوباره مشخص کن.")
                (action.parameters["amount"]?.toLongOrNull() ?: 0L) <= 0L -> ActionValidation.NeedsClarification("مبلغ هزینهٔ سفر را به تومان مشخص کن.")
                action.parameters["title"].isNullOrBlank() -> ActionValidation.NeedsClarification("عنوان هزینه چیست؟")
                action.parameters["payer"].isNullOrBlank() || trip.members.none { it == action.parameters["payer"] } -> ActionValidation.NeedsClarification("پرداخت‌کننده را از میان هم‌سفرها مشخص کن.")
                else -> ActionValidation.Valid
            }
        }
        else -> ActionValidation.Invalid("این عملیات سفر پشتیبانی نمی‌شود.")
    }
    override suspend fun execute(action: AssistantAction): AssistantToolResult {
        val trips = store.load()
        val number = NumberFormat.getNumberInstance(Locale("fa", "IR"))
        return when (action.operation) {
            "list" -> AssistantToolResult(true, if (trips.isEmpty()) "هنوز سفری ثبت نشده." else trips.joinToString("\n") { "• " + it.title + " — " + it.destination + "، " + it.expenses.size + " هزینه" })
            "add_expense" -> {
                val trip = trips.first { it.id == action.parameters["trip_id"] }
                val expense = TripExpense(UUID.randomUUID().toString(), action.parameters.getValue("title"), action.parameters.getValue("amount").toLong(), action.parameters.getValue("payer"))
                store.save(trips.map { if (it.id == trip.id) it.copy(expenses = it.expenses + expense) else it })
                AssistantToolResult(true, "هزینهٔ " + number.format(expense.amount) + " تومان برای سفر «" + trip.title + "» ثبت شد؛ پرداخت‌کننده: " + expense.payer + ".")
            }
            else -> AssistantToolResult(false, "عملیات سفر ناشناخته است.")
        }
    }
}

class MemoryAssistantTool(private val store: AssistantDataStore) : AssistantTool {
    override val module = AssistantModule.MEMORY
    override suspend fun validate(action: AssistantAction): ActionValidation = when (action.operation) {
        "list" -> ActionValidation.Valid
        "add_note" -> if (action.parameters["note"].isNullOrBlank()) ActionValidation.NeedsClarification("چه نکته‌ای را ذخیره کنم؟") else ActionValidation.Valid
        else -> ActionValidation.Invalid("این عملیات حافظه پشتیبانی نمی‌شود.")
    }
    override suspend fun execute(action: AssistantAction): AssistantToolResult = when (action.operation) {
        "list" -> {
            val notes = store.memories()
            AssistantToolResult(true, if (notes.isEmpty()) "حافظه هنوز یادداشتی ندارد." else notes.joinToString("\n") { "• " + it })
        }
        "add_note" -> {
            store.addMemory(action.parameters.getValue("note"))
            AssistantToolResult(true, "این نکته در حافظه ذخیره شد.")
        }
        else -> AssistantToolResult(false, "عملیات حافظه ناشناخته است.")
    }
}
