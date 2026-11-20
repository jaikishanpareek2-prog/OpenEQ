package app.openeq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

class PlayerService : Service() {
    companion object {
        @Volatile var running = false
    }

    private lateinit var session: MediaSession

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        EqEngine.init(this)
        Player.init(this)
        session = MediaSession(this, "OpenEQ")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { Player.resume() }
            override fun onPause() { Player.pause() }
            override fun onSkipToNext() { Player.next() }
            override fun onSkipToPrevious() { Player.prev() }
            override fun onSeekTo(pos: Long) { Player.seek(pos.toInt()) }
        })
        session.isActive = true
        Player.onChange = { refresh() }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("player", "Music player", NotificationManager.IMPORTANCE_LOW))
        val n = build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(2, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "toggle" -> Player.toggle()
            "next" -> Player.next()
            "prev" -> Player.prev()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        Player.onChange = null
        try { session.release() } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun refresh() {
        getSystemService(NotificationManager::class.java).notify(2, build())
    }

    private fun act(icon: Int, title: String, action: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, PlayerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun build(): Notification {
        val s = Player.current
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s?.title ?: "OpenEQ")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s?.artist ?: "")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Player.duration.toLong())
                .build()
        )
        val st = if (Player.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
                )
                .setState(st, Player.position.toLong(), 1f)
                .build()
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "player")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(s?.title ?: "OpenEQ")
            .setContentText(s?.artist ?: "")
            .setContentIntent(open)
            .setOngoing(Player.playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(act(android.R.drawable.ic_media_previous, "Previous", "prev"))
            .addAction(
                if (Player.playing) act(android.R.drawable.ic_media_pause, "Pause", "toggle")
                else act(android.R.drawable.ic_media_play, "Play", "toggle")
            )
            .addAction(act(android.R.drawable.ic_media_next, "Next", "next"))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }
}
