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
        // Daily journal intents use the registered assistant tool.
        if (text.contains("روزنگار") || text.contains("گزارش روزانه") || text.contains("اتفاقات امروز")) {
            val wantsRead = listOf("نمایش", "نشون بده", "بگو", "چی ثبت", "چه ثبت", "گزارش", "لیست", "ببینم", "بخوان").any { text.contains(it) } || text.contains("امروز") || text.contains("این هفته")
            if (wantsRead) {
                val period = if (text.contains("هفته")) "week" else if (text.contains("اخیر") || text.contains("آخرین")) "recent" else "today"
                return ready(AssistantModule.JOURNAL, "list", ActionAccess.READ, mapOf("period" to period))
            }
            val detail = raw.replace(Regex("(?i).*?(?:در روزنگار(?: هوشمند)?|گزارش روزانه)\\s*(?:ثبت کن|اضافه کن|یادداشت کن)?\\s*[:：-]?\\s*"), "").trim()
            if (detail.isBlank() || detail == raw.trim()) return AssistantIntentResolution.Clarify("چه اتفاقی را در روزنگار ثبت کنم؟ مثلاً: امروز جلسه کاری داشتم.")
            val category = when {
                listOf("ورزش", "دوچرخه", "دویدن", "پیاده‌روی", "تمرین").any { text.contains(it) } -> "ورزش"
                listOf("کار", "جلسه", "پروژه", "مشتری").any { text.contains(it) } -> "کار"
                listOf("خرید", "هزینه", "پرداخت").any { text.contains(it) } -> "مالی و خرید"
                listOf("سفر", "طبیعت", "گردش").any { text.contains(it) } -> "سفر و طبیعت"
                else -> "شخصی"
            }
            return ready(AssistantModule.JOURNAL, "add_entry", ActionAccess.WRITE, mapOf("category" to category, "title" to detail.take(48), "detail" to detail))
        }

        if (listOf("یادت باشه", "به خاطر بسپار", "این رو ذخیره کن", "این را ذخیره کن").any { text.contains(it) }) {
            val note = text.replace("یادت باشه", "").replace("به خاطر بسپار", "")
                .replace("این رو ذخیره کن", "").replace("این را ذخیره کن", "").trim()
            return if (note.isBlank()) AssistantIntentResolution.Clarify("دقیقاً چه نکته‌ای را در حافظه ذخیره کنم؟")
            else ready(AssistantModule.MEMORY, "add_note", ActionAccess.WRITE, mapOf("note" to note))
        }
        if (text.contains("حافظه") && listOf("چی", "لیست", "یادداشت").any { text.contains(it) })
            return ready(AssistantModule.MEMORY, "list", ActionAccess.READ)

        // Explicitly cancel an existing trip only after the router asks for confirmation.
        if (text.contains("سفر") && listOf("نمیخوام", "نمی‌خوام", "نمی خواهم", "نمی‌خواهم", "لغو", "کنسل", "انصراف").any { text.contains(it) }) {
            if (trips.isEmpty()) return AssistantIntentResolution.Clarify("سفری در فهرستت پیدا نکردم که لغوش کنیم. منظورت کدام سفر است؟")
            val destinationHint = Regex("""(?:سفر|رفتن)\s+(?:به\s+)?([آ-یء\s‌-]{2,30})""").find(raw)?.groupValues?.get(1)?.trim()
            val matches = if (destinationHint.isNullOrBlank()) trips else trips.filter {
                normalize(it.destination).contains(normalize(destinationHint)) ||
                    normalize(it.title).contains(normalize(destinationHint))
            }
            val trip = when {
                matches.size == 1 -> matches.first()
                matches.isEmpty() -> return AssistantIntentResolution.Clarify("سفر موردنظرت را پیدا نکردم. مقصد یا عنوان دقیق سفر را بگو.")
                else -> return AssistantIntentResolution.Clarify("چند سفر ثبت شده؛ بگو کدام سفر را می‌خواهی لغو کنی؟")
            }
            return ready(AssistantModule.TRAVEL, "delete_trip", ActionAccess.WRITE, mapOf("trip_id" to trip.id))
        }

        // Create a trip from natural future travel statements, including "دارم میرم سفر".
        val travelIntent = text.contains("سفر") || text.contains("مسافرت")
        val travelFuture = listOf("میخوام", "می‌خوام", "قصد دارم", "برنامه دارم", "دارم میرم", "دارم می‌روم", "میرم", "می‌روم", "راهی میشم", "راهی می‌شم").any { text.contains(it) }
        if (travelIntent && travelFuture) {
            val destination = Regex("""(?:سفر(?:\s+به)?|مسافرت(?:\s+به)?|برم(?:\s+به)?|بروم(?:\s+به)?|میرم(?:\s+به)?|می‌روم(?:\s+به)?)\s+(.+)$""")
                .find(text)?.groupValues?.get(1)?.trim()
                ?.replace(Regex("""(آخر هفته|این هفته|هفته آینده|هفته بعد|فردا|پس فردا|امروز).*?$"""), "")
                ?.trim(' ', '،', '.', '؟')
            if (destination.isNullOrBlank()) return AssistantIntentResolution.Clarify("مقصد سفرت کجاست؟ مثلاً بگو «دارم میرم سفر به شیراز».")
            val dates = when {
                text.contains("آخر هفته") -> "آخر هفته"
                text.contains("هفته آینده") || text.contains("هفته بعد") -> "هفته آینده"
                text.contains("پس فردا") -> "پس‌فردا"
                text.contains("فردا") -> "فردا"
                else -> "تاریخ ثبت نشده"
            }
            return ready(AssistantModule.TRAVEL, "create_trip", ActionAccess.WRITE, mapOf(
                "title" to "سفر به $destination", "destination" to destination, "dates" to dates
            ))
        }

        if (text.contains("سفر") && listOf("هزینه", "خرج", "پرداخت").any { text.contains(it) }) {
            if (hasAmbiguousToman(text)) return AssistantIntentResolution.Clarify("منظورت مبلغ واقعی به تومان است یا هزار تومان؟ مثلاً «۲۰۰ تومان» یا «۲۰۰ هزار تومان»؟")
            val amount = parseAmount(text) ?: return AssistantIntentResolution.Clarify("مبلغ هزینهٔ سفر را با واحد مشخص بگو؛ مثلاً «۲۵۰ هزار تومان»." )
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

        // Food entries are saved in the Health space, not only in the journal.
        val foodAction = listOf("خوردم", "مصرف کردم", "میل کردم", "نوشیدم").any { text.contains(it) }
        val foodMention = listOf("غذا", "صبحانه", "ناهار", "شام", "میان‌وعده", "برنج", "مرغ", "تخم مرغ", "نان", "ماست", "موز", "سیب", "بادام", "عدس", "تن ماهی", "شیر", "سیب‌زمینی").any { text.contains(it) }
        if (foodAction && foodMention) {
            val grams = parseGrams(text) ?: return AssistantIntentResolution.Clarify("مقدار غذا را به گرم بگو؛ مثلاً «۵۰۰ گرم برنج خوردم».")
            val known = listOf("سینه مرغ پخته", "تخم مرغ", "برنج سفید پخته", "نان سنگک", "ماست کم‌چرب", "موز", "سیب", "بادام", "عدس پخته", "تن ماهی آبکش‌شده", "شیر کم‌چرب", "سیب‌زمینی آب‌پز")
            val foodName = known.firstOrNull { normalize(text).contains(normalize(it)) }
                ?: listOf("برنج", "مرغ", "تخم مرغ", "نان", "ماست", "موز", "سیب", "بادام", "عدس", "تن ماهی", "شیر", "سیب زمینی").firstOrNull { normalize(text).contains(normalize(it)) }
                ?: "غذا"
            val meal = when { text.contains("صبحانه") -> "صبحانه"; text.contains("ناهار") -> "ناهار"; text.contains("شام") -> "شام"; else -> "میان‌وعده" }
            return ready(AssistantModule.HEALTH, "add_food", ActionAccess.WRITE, mapOf("food" to foodName, "grams" to grams.toString(), "meal" to meal))
        }

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
        val foodPurchase = listOf("غذا", "رستوران", "ناهار", "شام", "کافه", "سوپر").any { text.contains(it) } &&
            listOf("خوردم", "خوردیم", "گرفتم", "خریدم").any { text.contains(it) }
        val expense = listOf("خرج کردم", "هزینه کردم", "پرداخت کردم", "خریدم", "پول دادم", "هزینه").any { text.contains(it) } || foodPurchase
        if (income || expense) {
            if (hasAmbiguousToman(text)) return AssistantIntentResolution.Clarify("منظورت مبلغ واقعی به تومان است یا هزار تومان؟ مثلاً «۲۰۰ تومان» یا «۲۰۰ هزار تومان»؟")
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

    private fun parseGrams(text: String): Int? {
        val numeric = Regex("""(\d+(?:\.\d+)?)\s*(?:گرم|g)""").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        if (numeric != null) return numeric.toInt().takeIf { it > 0 }
        if (text.contains("نیم کیلو")) return 500
        val words = mapOf("صد" to 100, "دویست" to 200, "سیصد" to 300, "چهارصد" to 400, "پانصد" to 500, "ششصد" to 600, "هفتصد" to 700, "هشتصد" to 800, "نهصد" to 900, "یک کیلو" to 1000, "یه کیلو" to 1000)
        return words.entries.firstOrNull { text.contains(it.key) }?.value
    }

    private fun hasAmbiguousToman(text: String): Boolean {
        val match = Regex("""(\d+)\s*تومن""").find(text) ?: return false
        return (match.groupValues[1].toLongOrNull() ?: 0L) in 1L..999L
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

class HealthAssistantTool(context: Context) : AssistantTool {
    override val module = AssistantModule.HEALTH
    private val store = HStore(context.applicationContext)
    override suspend fun validate(action: AssistantAction): ActionValidation = when (action.operation) {
        "add_food" -> if ((action.parameters["grams"]?.toIntOrNull() ?: 0) > 0 && !action.parameters["food"].isNullOrBlank()) ActionValidation.Valid
            else ActionValidation.NeedsClarification("نام غذا و مقدار آن به گرم را مشخص کن.")
        "summary" -> ActionValidation.Valid
        else -> ActionValidation.Invalid("این عملیات سلامت پشتیبانی نمی‌شود.")
    }
    override suspend fun execute(action: AssistantAction): AssistantToolResult = when (action.operation) {
        "summary" -> {
            val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
            val rows = store.logs().filter { it.at >= start }
            AssistantToolResult(true, if (rows.isEmpty()) "امروز هنوز موردی در دفتر سلامت ثبت نشده." else
                "دفتر سلامت امروز:\\n" + rows.joinToString("\\n") { "• " + it.name + " — " + it.kcal + " kcal" })
        }
        "add_food" -> {
            val food = action.parameters.getValue("food")
            val grams = action.parameters.getValue("grams").toInt()
            val meal = action.parameters["meal"].orEmpty().ifBlank { "میان‌وعده" }
            val normalizedFood = food.replace('ي','ی').replace('ك','ک')
            val match = foodBank.firstOrNull { normalizedFood.contains(it.name) || it.name.contains(normalizedFood) }
            val calories = if (match != null) (match.kcal * grams / 100.0).toInt() else 0
            val protein = match?.let { it.protein * grams / 100.0 } ?: 0.0
            val carbs = match?.let { it.carbs * grams / 100.0 } ?: 0.0
            val fat = match?.let { it.fat * grams / 100.0 } ?: 0.0
            val label = (match?.name ?: food) + " • " + grams + " گرم" + if (match == null) " (کالری نامشخص)" else ""
            store.add(HLog(label, calories, "غذا • " + meal, System.currentTimeMillis(), protein, carbs, fat))
            AssistantToolResult(true, if (match == null)
                "مقدار «" + label + "» در دفتر سلامت ثبت شد. برای محاسبه کالری، نوع دقیق غذا را هم بگو."
            else "«" + label + "» در دفتر سلامت ثبت شد؛ انرژی تقریبی: " + calories + " کیلوکالری.")
        }
        else -> AssistantToolResult(false, "عملیات سلامت ناشناخته است.")
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
        "create_trip" -> when {
            action.parameters["destination"].isNullOrBlank() -> ActionValidation.NeedsClarification("مقصد سفر را مشخص کن.")
            action.parameters["title"].isNullOrBlank() -> ActionValidation.NeedsClarification("عنوان سفر را مشخص کن.")
            store.load().any { it.destination.equals(action.parameters["destination"], ignoreCase = true) && it.dates == action.parameters["dates"] } ->
                ActionValidation.NeedsClarification("سفری با همین مقصد و تاریخ از قبل ثبت شده است.")
            else -> ActionValidation.Valid
        }
        "delete_trip" -> if (store.load().any { it.id == action.parameters["trip_id"] }) ActionValidation.Valid
            else ActionValidation.NeedsClarification("سفر موردنظر پیدا نشد؛ فهرست سفرها را بررسی کن.")
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
            "create_trip" -> {
                val trip = LocalTrip(
                    UUID.randomUUID().toString(),
                    action.parameters.getValue("title"),
                    action.parameters.getValue("destination"),
                    action.parameters["dates"].orEmpty().ifBlank { "تاریخ ثبت نشده" },
                    listOf("من"),
                    emptyList()
                )
                store.save(listOf(trip) + trips)
                AssistantToolResult(true, "سفر «" + trip.title + "» به مقصد " + trip.destination + " در بخش سفرها ساخته شد.")
            }
            "delete_trip" -> {
                val trip = trips.firstOrNull { it.id == action.parameters["trip_id"] }
                    ?: return AssistantToolResult(false, "سفر موردنظر دیگر وجود ندارد.")
                store.save(trips.filterNot { it.id == trip.id })
                AssistantToolResult(true, "سفر «" + trip.title + "» و هزینه‌های ثبت‌شده‌اش حذف شد.")
            }
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

class JournalAssistantTool(context: Context) : AssistantTool {
    override val module = AssistantModule.JOURNAL
    private val store = DailyJournalStore(context.applicationContext)

    override suspend fun validate(action: AssistantAction): ActionValidation = when (action.operation) {
        "list" -> ActionValidation.Valid
        "add_entry" -> if (action.parameters["detail"].isNullOrBlank()) ActionValidation.NeedsClarification("چه اتفاقی را در روزنگار ثبت کنم؟") else ActionValidation.Valid
        else -> ActionValidation.Invalid("این عملیات روزنگار پشتیبانی نمی‌شود.")
    }

    override suspend fun execute(action: AssistantAction): AssistantToolResult {
        return when (action.operation) {
            "list" -> {
                val now = System.currentTimeMillis()
                val rows = when (action.parameters["period"]) {
                    "week" -> store.report(now - 7L * 24L * 60L * 60L * 1000L, now + 1L)
                    "recent" -> store.entries().take(10)
                    else -> store.report(DailyJournalStore.startOfDay(now), now + 1L)
                }
                val label = when (action.parameters["period"]) { "week" -> "هفت روز اخیر"; "recent" -> "آخرین رویدادها"; else -> "امروز" }
                val message = if (rows.isEmpty()) "در روزنگار برای «" + label + "» موردی ثبت نشده است." else "روزنگار " + label + " (" + rows.size + " مورد):\n" + rows.joinToString("\n") { "• " + DailyJournalStore.dateLabel(it.timestamp) + " | " + it.category + " | " + it.title + ": " + it.detail }
                AssistantToolResult(true, message)
            }
            "add_entry" -> {
                val item = store.add(action.parameters["category"].orEmpty().ifBlank { "شخصی" }, action.parameters["title"].orEmpty().ifBlank { "رویداد روزانه" }, action.parameters.getValue("detail"))
                AssistantToolResult(true, "رویداد در روزنگار ثبت شد: " + item.category + " — " + item.title)
            }
            else -> AssistantToolResult(false, "عملیات روزنگار ناشناخته است.")
        }
    }
}
