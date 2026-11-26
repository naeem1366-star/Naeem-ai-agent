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
data class BillingCatalog(val balanceToman:Long,val plans:List<BillingPlan>,val paymentInstructions:String,val minTopupToman:Long,val maxTopupToman:Long,val subscriptionName:String?)
data class BillingSnapshot(val catalog:BillingCatalog,val orders:List<BillingOrder>)

class BillingClient(private val gatewayUrl:String) {
    private val http=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).writeTimeout(20,TimeUnit.SECONDS).build()

    private suspend fun call(path:String,token:String,method:String="GET",body:JSONObject?=null):JSONObject=withContext(Dispatchers.IO) {
        val root=gatewayUrl.trim().trimEnd('/')
        require(root.startsWith("https://",true)) { "درگاه امن EMA هنوز برای این نسخه تنظیم نشده است." }
        require(token.isNotBlank()) { "نشست ورود منقضی شده است؛ دوباره وارد حساب شو." }
        val builder=Request.Builder().url(root+path).header("Authorization","Bearer "+token).header("Accept","application/json")
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
            catalogJson.optLong("balanceToman"),plans,payment.optString("instructions"),
            payment.optLong("minTopupToman",100000),payment.optLong("maxTopupToman",50000000),
            subscription?.optString("plan_name")?.takeIf { it.isNotBlank() }
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
}
