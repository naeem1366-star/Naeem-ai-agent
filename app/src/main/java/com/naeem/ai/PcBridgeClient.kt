package com.naeem.ai

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier

data class PcBridgeDevice(val name: String, val ip: String, val port: Int, val fingerprint: String)

class PcBridgeClient(
    private val onStatus: (String) -> Unit,
    private val onScreen: (ByteArray) -> Unit
) {
    @Volatile private var socket: WebSocket? = null
    @Volatile private var http: OkHttpClient? = null
    @Volatile private var connected = false

    suspend fun discover(): List<PcBridgeDevice> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, PcBridgeDevice>()
        DatagramSocket().use { datagram ->
            datagram.broadcast = true
            datagram.soTimeout = 350
            val payload = "NAEEM_BRIDGE_DISCOVER".toByteArray(Charsets.UTF_8)
            datagram.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), 8766))
            val deadline = System.currentTimeMillis() + 1800
            while (System.currentTimeMillis() < deadline) {
                try {
                    val buffer = ByteArray(4096)
                    val packet = DatagramPacket(buffer, buffer.size)
                    datagram.receive(packet)
                    val json = JSONObject(String(packet.data, 0, packet.length, Charsets.UTF_8))
                    val ip = json.optString("ip", packet.address.hostAddress.orEmpty())
                    if (ip.isNotBlank()) {
                        found[ip] = PcBridgeDevice(
                            json.optString("name", "Naeem AI Desktop"),
                            ip,
                            json.optInt("port", 8765),
                            json.optString("fingerprint", "")
                        )
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Keep listening until the discovery window ends.
                } catch (_: Exception) {
                    // Ignore malformed or unrelated LAN packets.
                }
            }
        }
        found.values.toList()
    }

    suspend fun connect(ip: String, port: Int, pin: String, fingerprint: String) = withContext(Dispatchers.IO) {
        disconnect()
        val normalizedFingerprint = fingerprint.filter { it.isLetterOrDigit() }.uppercase()
        require(normalizedFingerprint.length == 64) { "اثر انگشت امنیتی معتبر نیست؛ آن را دقیقاً از پنجره کامپیوتر وارد کن." }
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val certificate = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("گواهی سرور دریافت نشد.")
                certificate.checkValidity()
                val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                    .joinToString("") { "%02X".format(it) }
                if (!actual.equals(normalizedFingerprint, ignoreCase = true)) {
                    throw java.security.cert.CertificateException("اثر انگشت گواهی با کامپیوتر انتخاب‌شده مطابقت ندارد.")
                }
            }
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        http = client
        val pairJson = JSONObject().put("pin", pin).toString()
        val request = Request.Builder()
            .url("https://$ip:$port/pair")
            .post(pairJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val token = client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                throw IllegalStateException(message.ifBlank { "جفت‌سازی ناموفق بود (" + response.code + ")." })
            }
            JSONObject(body).getString("token")
        }
        val wsRequest = Request.Builder().url("wss://$ip:$port/ws?token=$token").build()
        val opened = java.util.concurrent.CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<String?>(null)
        socket = client.newWebSocket(wsRequest, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                onStatus("گوشی به کامپیوتر وصل شد؛ دریافت تصویر صفحه فعال است.")
                opened.countDown()
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { onScreen(bytes.toByteArray()) }
            override fun onMessage(webSocket: WebSocket, text: String) { onStatus(text) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                failure.set(t.message ?: "اتصال قطع شد.")
                onStatus("ارتباط قطع شد: " + (t.message ?: "خطای شبکه"))
                opened.countDown()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                onStatus("اتصال بسته شد.")
            }
        })
        if (!opened.await(12, TimeUnit.SECONDS)) {
            disconnect()
            throw IllegalStateException("پاسخی از کامپیوتر دریافت نشد؛ فایروال ویندوز و شبکه را بررسی کن.")
        }
        failure.get()?.let { throw IllegalStateException(it) }
    }

    fun sendMouseMove(x: Float, y: Float) = send(JSONObject().put("type", "mouse_move").put("x", x).put("y", y))
    fun click() = send(JSONObject().put("type", "mouse_click"))
    fun key(key: String) = send(JSONObject().put("type", "key").put("key", key))
    fun typeText(value: String) = send(JSONObject().put("type", "type_text").put("text", value.take(2000)))
    fun launchApp(app: String) = send(JSONObject().put("type", "launch_app").put("app", app))

    private fun send(json: JSONObject) {
        if (connected) socket?.send(json.toString()) else onStatus("ابتدا به کامپیوتر وصل شو.")
    }

    fun disconnect() {
        connected = false
        socket?.close(1000, "User disconnected")
        socket = null
        http?.dispatcher?.executorService?.shutdown()
        http?.connectionPool?.evictAll()
        http = null
    }
}
