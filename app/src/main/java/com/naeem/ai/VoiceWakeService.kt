package com.naeem.ai

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.naeem.ai.core.assistant.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * User-started foreground voice assistant.
 * The service listens for the wake phrase, then handles clear food/workout
 * logging locally through the same validated assistant tools as in-app chat.
 */
class VoiceWakeService : Service() {
    companion object {
        const val ACTION_STOP = "com.naeem.ai.action.STOP_VOICE_WAKE"
        private const val CHANNEL_ID = "ema_voice_wake_silent_v2"
        private const val NOTIFICATION_ID = 7312
        private const val PREFS = "ima_voice_wake"
        private const val PREF_ENABLED = "enabled"
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private var listeningStartPending = false
    private var consecutiveErrors = 0
    private var commandArmed = false
    private var smartListening = false
    private var restartAfterSpeech = false
    private var destroyed = false
    private var segmentCommandHandled = false
    private var commandInFlight = false
    private lateinit var router: AssistantActionRouter

    override fun onCreate() {
        super.onCreate()
        router = AssistantActionRouter(
            ToolRegistry(
                listOf(
                    HealthAssistantTool(applicationContext),
                    SportsAssistantTool(applicationContext)
                )
            )
        )
        createNotificationChannel()
        smartListening = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("smart_listening", false)
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val languageResult = tts?.setLanguage(Locale("fa", "IR"))
                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = false
                }
                tts?.setSpeechRate(0.94f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        main.post {
                            commandInFlight = false
                            if (restartAfterSpeech && !destroyed) {
                                restartAfterSpeech = false
                                startListening()
                            }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post {
                            commandInFlight = false
                            if (restartAfterSpeech && !destroyed) {
                                restartAfterSpeech = false
                                main.postDelayed({ startListening() }, 700)
                            }
                        }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
                })
            }
            // Listening starts only after onStartCommand has entered foreground state.
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        // Do not advertise the voice service as active until the recognizer is actually ready.
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_ENABLED, true)
            .putBoolean("ready", false)
            .putString("state", "starting")
            .apply()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification("در حال آماده‌سازی میکروفون و تشخیص گفتار…"))
        startListening()
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "دستیار صوتی ایما",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "وقتی دستیار صوتی فعال است، ایما منتظر شنیدن کلمه بیدارباش می‌ماند."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String? = null): Notification {
        val openApp = PendingIntent.getActivity(
            this, 7313, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this, 7314, Intent(this, VoiceWakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (smartListening) "ایما در حالت شنود هوشمند است" else "ایما آماده شنیدن است")
            .setContentText(message ?: if (smartListening) "شنود هوشمند فعال است. برای توقف بگو «ایما، شنود رو خاموش کن» یا دکمه توقف را بزن." else "برای شروع بگو «ایما». برای فعال‌سازی شنود هوشمند بگو «به گوش باش». برای خاموش‌کردن کامل، توقف را بزن.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "توقف", stopService)
            .build()
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setVoiceState("error")
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID, buildNotification("سرویس تشخیص گفتار روی این گوشی در دسترس نیست.")
            )
            stopSelf()
            return
        }
        // Use the system recognition service so Persian (fa-IR) can use its installed
        // online language provider. An available on-device engine does not imply Persian support.
        recognizer = runCatching {
            SpeechRecognizer.createSpeechRecognizer(this)
        }.getOrElse {
            setVoiceState("error")
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID, buildNotification("راه‌اندازی موتور تشخیص گفتار ممکن نشد.")
            )
            null
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listeningStartPending = false
                listening = true
                consecutiveErrors = 0
                setVoiceState("listening")
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                listening = false
                listeningStartPending = false
                setVoiceState("processing")
            }
            override fun onEndOfSegmentedSession() {
                listening = false
                listeningStartPending = false
                setVoiceState("processing")
                if (destroyed || commandInFlight || restartAfterSpeech || (tts?.isSpeaking == true)) return
                main.postDelayed({ startListening() }, 250)
            }
            override fun onError(error: Int) {
                listening = false
                listeningStartPending = false
                setVoiceState("retrying")
                if (destroyed) return
                consecutiveErrors++
                // Some Android speech providers leave the recognizer busy after an error.
                // Recreate it before retrying instead of repeatedly starting a poisoned instance.
                runCatching { recognizer?.cancel() }
                runCatching { recognizer?.destroy() }
                recognizer = null
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    setVoiceState("error")
                    stopSelf()
                    return
                }
                // Keep the user-started foreground service alive and retry with backoff.
                // The UI uses a separate ready flag and never paints a failed recognizer green.
                val baseDelay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1800L
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> 3000L
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> 5000L
                    else -> 1200L
                }
                val delayMs = (baseDelay * (1L shl (consecutiveErrors.coerceAtMost(4) - 1).coerceAtLeast(0))).coerceAtMost(20000L)
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID, buildNotification("اتصال شنود ناپایدار شد؛ در حال تلاش مجدد…")
                )
                main.postDelayed({ startListening() }, delayMs)
            }
            override fun onResults(results: Bundle?) {
                listening = false
                listeningStartPending = false
                setVoiceState("processing")
                if (segmentCommandHandled) { segmentCommandHandled = false; return }
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty().trim()
                handleRecognizedText(spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun startListening() {
        if (destroyed || listening || listeningStartPending) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setVoiceState("error")
            stopSelf()
            return
        }
        ensureRecognizer()
        val engine = recognizer ?: return
        listeningStartPending = true
        segmentCommandHandled = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            // Do not force offline recognition: Persian offline packs are absent on many devices.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, Build.VERSION.SDK_INT >= 33)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= 33) {
                // A segmented session keeps one recognizer session open across pauses instead of
                // restarting every second (which causes the repeated system "ding" on Samsung).
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
            } else {
                // Older recognition services are one-shot; use a longer idle window to avoid a beep loop.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            }
        }
        runCatching {
            engine.startListening(intent)
        }.onFailure {
            listening = false
            listeningStartPending = false
            setVoiceState("error")
            main.postDelayed({ startListening() }, 1400)
        }
    }

    private fun isCommandCandidate(raw: String): Boolean {
        if (smartListening || commandArmed) return true
        val normalized = normalize(raw)
        return listOf("ایما جان", "hey ema", "هی ایما", "ایما", "ima").any { normalized.contains(it) }
    }

    private fun handleRecognizedText(raw: String) {
        if (raw.isBlank()) {
            main.postDelayed({ startListening() }, 500)
            return
        }
        if (smartListening && isStopListeningPhrase(raw)) {
            setSmartListening(false)
            speak("شنود هوشمند خاموش شد. ایما همچنان آماده شنیدن است.", true)
            return
        }
        if (smartListening) {
            processCommand(raw)
            return
        }
        if (commandArmed) {
            commandArmed = false
            processCommand(raw)
            return
        }
        val normalized = normalize(raw)
        val wakeWords = listOf("ایما جان", "hey ema", "هی ایما", "ایما", "ima")
        val wake = wakeWords.firstOrNull { normalized.contains(it) }
        if (wake == null) {
            main.postDelayed({ startListening() }, 450)
            return
        }
        val command = normalized.substringAfter(wake, "").trim(' ', '،', ',', '.', '؛', ':')
        commandArmed = true
        if (command.isBlank()) {
            speak("سلام، در خدمتم.", true)
        } else {
            commandArmed = false
            processCommand(command)
        }
    }

    private fun processCommand(command: String) {
        commandInFlight = true
        val normalized = normalize(command)
        if (normalized.contains("به گوش باش") || normalized.contains("بگوش باش")) {
            setSmartListening(true)
            speak("شنود هوشمند فعال شد. برای توقف بگو ایما، شنود رو خاموش کن.", true)
            return
        }
        if (isStopListeningPhrase(normalized)) {
            setSmartListening(false)
            speak("شنود هوشمند خاموش شد. ایما همچنان آماده شنیدن است.", true)
            return
        }
        scope.launch {
            val reply = when (val resolution = PersianAssistantIntentResolver.resolve(command)) {
                AssistantIntentResolution.NoMatch ->
                    "این درخواست را هنوز نمی‌توانم خودکار انجام بدهم. فعلاً ثبت غذا و تمرین و نمایش خلاصه آن‌ها فعال است."
                is AssistantIntentResolution.Clarify -> {
                    commandArmed = true
                    resolution.question
                }
                is AssistantIntentResolution.Ready -> {
                    when (val result = router.route(resolution.action)) {
                        is ActionRouteResult.Completed -> result.result.message
                        is ActionRouteResult.ConfirmationRequired -> {
                            // A clear spoken logging statement is itself the user's instruction to save it.
                            when (val saved = router.confirm(result.action.id)) {
                                is ActionRouteResult.Completed -> saved.result.message
                                is ActionRouteResult.ClarificationRequired -> {
                                    commandArmed = true
                                    saved.question
                                }
                                is ActionRouteResult.Rejected -> saved.reason
                                is ActionRouteResult.Failed -> saved.reason
                                is ActionRouteResult.ConfirmationRequired -> "ثبت این مورد انجام نشد."
                            }
                        }
                        is ActionRouteResult.ClarificationRequired -> {
                            commandArmed = true
                            result.question
                        }
                        is ActionRouteResult.Rejected -> result.reason
                        is ActionRouteResult.Failed -> result.reason
                    }
                }
            }
            speak(reply, true)
        }
    }

    private fun isStopListeningPhrase(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.contains("شنود رو خاموش کن") ||
            normalized.contains("شنود را خاموش کن") ||
            normalized.contains("شنود رو متوقف کن") ||
            normalized.contains("شنود را متوقف کن")
    }

    private fun setSmartListening(enabled: Boolean) {
        smartListening = enabled
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("smart_listening", enabled).apply()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun setVoiceState(state: String) {
        val edit = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", state)
            .putBoolean("ready", state == "listening")
        if (state == "listening") edit.putBoolean(PREF_ENABLED, true)
        if (state == "error" || state == "stopped") edit.putBoolean(PREF_ENABLED, false)
        edit.apply()
    }

    private fun speak(text: String, listenAgain: Boolean) {
        restartAfterSpeech = listenAgain
        val engine = tts
        if (!ttsReady || engine == null) {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildNotification(text.take(180))
            )
            main.postDelayed({ commandInFlight = false; if (listenAgain) startListening() }, 900)
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            restartAfterSpeech = false
            main.postDelayed({ commandInFlight = false; if (listenAgain) startListening() }, 900)
        }
    }

    private fun normalize(value: String): String =
        value.replace('ي', 'ی').replace('ك', 'ک')
            .map { c ->
                when (c) {
                    in '۰'..'۹' -> ('0'.code + (c - '۰')).toChar()
                    in '٠'..'٩' -> ('0'.code + (c - '٠')).toChar()
                    else -> c
                }
            }.joinToString("").lowercase(Locale.ROOT)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        listening = false
        listeningStartPending = false
        restartAfterSpeech = false
        commandInFlight = false
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("ready", false)
            .putBoolean(PREF_ENABLED, false)
            .putBoolean("smart_listening", false)
            .putString("state", "stopped")
            .apply()
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        scope.cancel()
        super.onDestroy()
    }
}
