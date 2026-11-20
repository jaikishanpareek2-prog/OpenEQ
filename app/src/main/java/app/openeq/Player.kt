package app.openeq

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

data class Song(val id: Long, val title: String, val artist: String) {
    fun uri(): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

object Player {
    val songs = mutableStateListOf<Song>()
    val queue = mutableStateListOf<Song>()
    val playlists = mutableStateMapOf<String, List<Long>>()
    var index by mutableIntStateOf(-1)
    var playing by mutableStateOf(false)
    var position by mutableIntStateOf(0)
    var duration by mutableIntStateOf(0)
    val current: Song? get() = queue.getOrNull(index)
    var onChange: (() -> Unit)? = null

    private var mp: MediaPlayer? = null
    private var ready = false
    private lateinit var ctx: Context

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { f ->
        if (f == AudioManager.AUDIOFOCUS_LOSS || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
    }

    fun init(c: Context) {
        if (ready) return
        ready = true
        ctx = c.applicationContext
        try {
            ctx.getSharedPreferences("openeq_player", Context.MODE_PRIVATE).getString("pl", "")
                ?.split(";")?.filter { it.contains("|") }?.forEach {
                    val p = it.split("|")
                    playlists[p[0]] = p[1].split(",").filter { x -> x.isNotEmpty() }.map { x -> x.toLong() }
                }
        } catch (_: Exception) {}
    }

    private fun savePl() {
        ctx.getSharedPreferences("openeq_player", Context.MODE_PRIVATE).edit()
            .putString("pl", playlists.entries.joinToString(";") { it.key + "|" + it.value.joinToString(",") })
            .apply()
    }

    fun load() {
        val list = ArrayList<Song>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION
        )
        try {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getLong(3) >= 30000L) {
                        list.add(Song(c.getLong(0), c.getString(1) ?: "Unknown", c.getString(2) ?: "Unknown"))
                    }
                }
            }
        } catch (_: Exception) {}
        songs.clear()
        songs.addAll(list)
    }

    private fun focus() {
        try {
            ctx.getSystemService(AudioManager::class.java).requestAudioFocus(
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
            )
        } catch (_: Throwable) {}
    }

    fun playList(list: List<Song>, start: Int) {
        val copy = list.toList()
        queue.clear()
        queue.addAll(copy)
        play(start)
    }

    fun play(i: Int) {
        if (i !in queue.indices) return
        index = i
        position = 0
        mp?.let { old ->
            try { EqEngine.detach(old.audioSessionId) } catch (_: Throwable) {}
            try { old.release() } catch (_: Throwable) {}
        }
        mp = null
        focus()
        val m = MediaPlayer()
        mp = m
        try {
            m.setAudioAttributes(attrs)
            m.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            m.setDataSource(ctx, queue[i].uri())
            m.setOnPreparedListener { p ->
                p.start()
                playing = true
                duration = p.duration
                EqEngine.attach(p.audioSessionId)
                onChange?.invoke()
            }
            m.setOnCompletionListener { PlayMode.onDone() }
            m.setOnErrorListener { _, _, _ -> playing = false; onChange?.invoke(); true }
            m.prepareAsync()
        } catch (_: Throwable) {
            playing = false
        }
        if (!PlayerService.running) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, PlayerService::class.java))
        }
        onChange?.invoke()
    }

    fun pause() {
        try { mp?.pause() } catch (_: Throwable) {}
        playing = false
        onChange?.invoke()
    }

    fun resume() {
        val m = mp
        if (m == null) {
            if (queue.isNotEmpty()) play(maxOf(index, 0))
            else if (songs.isNotEmpty()) playList(songs, 0)
            return
        }
        focus()
        try { m.start() } catch (_: Throwable) {}
        playing = true
        onChange?.invoke()
    }

    fun toggle() { if (playing) pause() else resume() }

    fun sessionId(): Int = try { mp?.audioSessionId ?: 0 } catch (_: Throwable) { 0 }

    fun next() { if (queue.isNotEmpty()) play(PlayMode.pickNext()) }

    fun prev() {
        if (queue.isEmpty()) return
        if (position > 3000) seek(0) else play(if (index - 1 < 0) queue.size - 1 else index - 1)
    }

    fun seek(ms: Int) {
        try { mp?.seekTo(ms); position = ms; onChange?.invoke() } catch (_: Throwable) {}
    }

    fun tick() {
        val m = mp ?: return
        try { if (m.isPlaying) position = m.currentPosition } catch (_: Throwable) {}
    }

    fun playNext(s: Song) {
        if (queue.isEmpty()) playList(listOf(s), 0) else queue.add(minOf(index + 1, queue.size), s)
    }

    fun addToQueue(s: Song) {
        if (queue.isEmpty()) playList(listOf(s), 0) else queue.add(s)
    }

    fun newPlaylist(): String {
        var n = 1
        while (playlists.containsKey("Playlist $n")) n++
        val name = "Playlist $n"
        playlists[name] = emptyList()
        savePl()
        return name
    }

    fun addToPlaylist(name: String, s: Song) {
        val cur = playlists[name] ?: emptyList()
        if (!cur.contains(s.id)) playlists[name] = cur + s.id
        savePl()
    }

    fun playlistSongs(name: String): List<Song> =
        (playlists[name] ?: emptyList()).mapNotNull { id -> songs.firstOrNull { it.id == id } }
}
