package com.naeem.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class BillingPlan(val id:String,val name:String,val durationDays:Int,val priceToman:Long,val aiCreditPercent:Int)
data class BillingOrder(val id:String,val amountToman:Long,val purpose:String,val status:String,val receiptSubmitted:Boolean,val rejectionReason:String)
data class BillingCatalog(val balanceCredits:Long,val plans:List<BillingPlan>,val paymentInstructions:String,val minTopupToman:Long,val maxTopupToman:Long,val subscriptionName:String?,val isAdmin:Boolean)
data class BillingAdminOrder(val id:String,val userId:String,val amountToman:Long,val purpose:String,val reference:String,val createdAt:String)
data class BillingSnapshot(val catalog:BillingCatalog,val orders:List<BillingOrder>)
data class BillingAdminDashboard(val summary:JSONObject,val users:List<JSONObject>,val orders:List<JSONObject>,val subscriptions:List<JSONObject>,val providerCosts:List<JSONObject>,val paymentStatus:List<JSONObject>,val recentAudit:List<JSONObject>,val providerAccounts:List<JSONObject>)

class BillingClient(private val gatewayUrl:String) {
    private val http=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).writeTimeout(20,TimeUnit.SECONDS).build()

    private suspend fun call(path:String,token:String,method:String="GET",body:JSONObject?=null):JSONObject=withContext(Dispatchers.IO) {
        val root=gatewayUrl.trim().trimEnd('/')
        require(root.startsWith("https://",true)) { "درگاه امن EMA هنوز برای این نسخه تنظیم نشده است." }
        require(token.isNotBlank()) { "نشست ورود منقضی شده است؛ دوباره وارد حساب شو." }
        val builder=Request.Builder().url(root+path).header("apikey",EmaSupabaseConfig.PUBLISHABLE_KEY).header("Authorization","Bearer "+token).header("Accept","application/json")
        val requestBody=body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        when(method) {
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody("application/json".toMediaType()))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody("application/json".toMediaType()))
            else -> builder.get()
        }
        http.newCall(builder.build()).execute().use { response ->
            val raw=response.body?.string().orEmpty()
            val parsed=runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if(!response.isSuccessful) {
                val err=parsed.optJSONObject("error")
                val message=err?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: err?.optString("code")?.takeIf { it.isNotBlank() }
                    ?: parsed.optString("error").ifBlank { "HTTP "+response.code }
                throw IllegalStateException(message)
            }
            parsed
        }
    }

    suspend fun hasPaidAccess(token:String):Boolean {
        val catalog = call("/v1/billing/catalog", token)
        val subscription = catalog.optJSONObject("subscription")
        return catalog.optBoolean("isAdmin", false) ||
            catalog.optLong("balanceCredits") > 0L ||
            !subscription?.optString("plan_name").isNullOrBlank()
    }

    suspend fun load(token:String):BillingSnapshot {
        val catalogJson=call("/v1/billing/catalog",token)
        val ordersJson=call("/v1/billing/orders",token)
        val plansJson=catalogJson.optJSONArray("plans") ?: JSONArray()
        val plans=(0 until plansJson.length()).mapNotNull { i -> plansJson.optJSONObject(i)?.let { p ->
            BillingPlan(p.optString("id"),p.optString("name"),p.optInt("duration_days"),p.optLong("price_toman"),p.optInt("ai_credit_percent",50))
        } }
        val payment=catalogJson.optJSONObject("payment") ?: JSONObject()
        val subscription=catalogJson.optJSONObject("subscription")
        val catalog=BillingCatalog(
            catalogJson.optLong("balanceCredits"),plans,payment.optString("instructions"),
            payment.optLong("minTopupToman",500000),payment.optLong("maxTopupToman",50000000),
            subscription?.optString("plan_name")?.takeIf { it.isNotBlank() },
            catalogJson.optBoolean("isAdmin",false)
        )
        val array=ordersJson.optJSONArray("orders") ?: JSONArray()
        val orders=(0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { o ->
            BillingOrder(o.optString("id"),o.optLong("amount_toman"),o.optString("purpose"),o.optString("status"),
                o.optLong("receipt_submitted_at")>0,o.optString("rejection_reason"))
        } }
        return BillingSnapshot(catalog,orders)
    }

    suspend fun createOrder(token:String,purpose:String,amountToman:Long=0,planId:String?=null,idempotencyKey:String=UUID.randomUUID().toString()) {
        val body=JSONObject().put("purpose",purpose).put("idempotencyKey",idempotencyKey)
        if(purpose=="wallet_topup") body.put("amountToman",amountToman)
        if(planId!=null) body.put("planId",planId)
        call("/v1/billing/orders",token,"POST",body)
    }

    suspend fun submitReceipt(token:String,orderId:String,reference:String) {
        call("/v1/billing/orders/"+orderId+"/receipt",token,"POST",JSONObject().put("receiptReference",reference.trim()))
    }

    suspend fun adminOrders(token:String):List<BillingAdminOrder> {
        val json=call("/v1/billing/admin/orders",token)
        val array=json.optJSONArray("orders") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { o ->
            BillingAdminOrder(o.optString("id"),o.optString("user_id"),o.optLong("amount_toman"),
                o.optString("order_type"),o.optString("payment_reference"),o.optString("created_at"))
        } }
    }

    suspend fun reviewOrder(token:String,orderId:String,decision:String,reason:String="") {
        call("/v1/billing/admin/orders/"+orderId+"/review",token,"POST",
            JSONObject().put("decision",decision).put("reason",reason))
    }
    suspend fun adminDashboard(token:String):BillingAdminDashboard {
        val json=call("/v1/billing/admin/dashboard",token)
        fun objects(key:String):List<JSONObject> {
            val array=json.optJSONArray(key) ?: JSONArray()
            return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i) }
        }
        return BillingAdminDashboard(
            json.optJSONObject("summary") ?: JSONObject(),
            objects("users"),
            objects("orders"),
            objects("subscriptions"),
            objects("provider_costs"),
            objects("payment_status"),
            objects("recent_audit"),
            objects("provider_accounts")
        )
    }

}
