package com.naeem.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class PodcastPlaybackService : Service() {
    companion object {
        const val ACTION_PLAY = "com.naeem.ai.podcast.PLAY"
        const val ACTION_TOGGLE = "com.naeem.ai.podcast.TOGGLE"
        const val ACTION_STOP = "com.naeem.ai.podcast.STOP"
        const val ACTION_SEEK = "com.naeem.ai.podcast.SEEK"
        const val ACTION_STATUS = "com.naeem.ai.podcast.STATUS"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SHOW = "show"
        const val EXTRA_POSITION = "position"
        private const val CHANNEL_ID = "supra_podcast_playback"
        private const val NOTIFICATION_ID = 4207
    }

    private var player: MediaPlayer? = null
    private var session: MediaSessionCompat? = null
    private var title = ""
    private var show = ""
    private var audioUrl = ""
    private var duration = 0
    private var prepared = false
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            publish(position)
            if (isPlaying) handler.postDelayed(this, 700)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "SupraPodcast").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resumePlayback()
                override fun onPause() = pausePlayback()
                override fun onStop() = stopPlayback()
                override fun onSeekTo(pos: Long) {
                    runCatching { player?.seekTo(pos.toInt().coerceAtLeast(0)) }
                    publish(pos.toInt())
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startEpisode(
                intent.getStringExtra(EXTRA_URL).orEmpty(),
                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(EXTRA_SHOW).orEmpty()
            )
            ACTION_TOGGLE -> if (isPlaying) pausePlayback() else resumePlayback()
            ACTION_STOP -> stopPlayback()
            ACTION_SEEK -> {
                val position = intent.getIntExtra(EXTRA_POSITION, 0).coerceAtLeast(0)
                runCatching { player?.seekTo(position) }
                publish(position)
            }
        }
        return START_NOT_STICKY
    }

    private fun startEpisode(url: String, episodeTitle: String, showTitle: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        releasePlayer()
        audioUrl = url
        title = episodeTitle
        show = showTitle
        prepared = false
        isPlaying = false
        duration = 0
        startForeground(NOTIFICATION_ID, buildNotification())
        val mp = MediaPlayer()
        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
        player = mp
        mp.setOnPreparedListener {
            prepared = true
            duration = it.duration.coerceAtLeast(0)
            it.start()
            isPlaying = true
            updateSession(0)
            updateNotification()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
            publish(0)
        }
        mp.setOnCompletionListener {
            isPlaying = false
            updateSession(duration)
            updateNotification()
            publish(duration)
        }
        mp.setOnErrorListener { _, what, extra ->
            isPlaying = false
            publishError("پخش فایل صوتی ناموفق بود ($what/$extra)")
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            true
        }
        runCatching { mp.setDataSource(url); mp.prepareAsync() }.onFailure {
            publishError(it.message ?: "آماده‌سازی فایل صوتی ناموفق بود")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        updateSession(0)
    }

    private fun resumePlayback() {
        if (!prepared) return
        runCatching { player?.start() }
        isPlaying = true
        updateSession(currentPosition())
        updateNotification()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        publish(currentPosition())
    }

    private fun pausePlayback() {
        if (!prepared) return
        runCatching { player?.pause() }
        isPlaying = false
        handler.removeCallbacks(ticker)
        updateSession(currentPosition())
        updateNotification()
        publish(currentPosition())
    }

    private fun stopPlayback() {
        releasePlayer()
        publish(0)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun currentPosition() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    private fun releasePlayer() {
        handler.removeCallbacks(ticker)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        prepared = false
        isPlaying = false
        duration = 0
    }

    private fun updateSession(position: Int) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else if (prepared) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_BUFFERING
        session?.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, show)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.toLong())
            .build())
        session?.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SEEK_TO)
            .setState(state, position.toLong(), if (isPlaying) 1f else 0f)
            .build())
    }

    private fun buildNotification(): Notification {
        val toggleIntent = PendingIntent.getService(this, 1, Intent(this, PodcastPlaybackService::class.java).setAction(ACTION_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 2, Intent(this, PodcastPlaybackService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { "پادکست در حال آماده‌سازی" })
            .setContentText(show.ifBlank { "Supra AI" })
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying || !prepared)
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (isPlaying) "مکث" else "ادامه", toggleIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "توقف", stopIntent)
            .setStyle(MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun publish(position: Int) {
        updateSession(position)
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName)
            .putExtra(EXTRA_URL, audioUrl)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_SHOW, show)
            .putExtra(EXTRA_POSITION, position)
            .putExtra("duration", duration)
            .putExtra("playing", isPlaying)
            .putExtra("prepared", prepared))
    }

    private fun publishError(message: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra("error", message))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "پخش پادکست", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playback alive when the app is swiped away; the user can stop it from the notification.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releasePlayer()
        session?.isActive = false
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
