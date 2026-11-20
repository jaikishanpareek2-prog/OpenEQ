package app.openeq

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val C_PANEL = Color(0xFF00222A)
private val C_TEAL = Color(0xFF0F6C7C)
private val C_GREEN = Color(0xFF3CFF14)
private val C_TXT = Color(0xFF8FEBFF)
private val C_SUB = Color(0xFF6FA8B5)
private val C_TRACK = Color(0xFF3A4350)

private fun mmss(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun SeekBar() {
    Canvas(
        Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
            detectTapGestures { o ->
                if (Player.duration > 0) Player.seek((o.x / size.width * Player.duration).toInt())
            }
        }
    ) {
        val y = size.height / 2
        drawLine(C_TRACK, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), StrokeCap.Round)
        val f = if (Player.duration > 0) Player.position.toFloat() / Player.duration else 0f
        drawLine(C_GREEN, Offset(0f, y), Offset(size.width * f.coerceIn(0f, 1f), y), 4.dp.toPx(), StrokeCap.Round)
    }
    Text(mmss(Player.position) + " / " + mmss(Player.duration), color = C_SUB, fontSize = 12.sp)
}

@Composable
fun TransportRow() {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Prev", false, Modifier.weight(1f)) { Player.prev() }
        Pill(if (Player.playing) "Pause" else "Play", true, Modifier.weight(1f)) { Player.toggle() }
        Pill("Next", false, Modifier.weight(1f)) { Player.next() }
    }
}

@Composable
fun SongRow(s: Song, current: Boolean, onPlay: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (current) C_PANEL else Color.Transparent)
            .clickable { onPlay() }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.title, color = if (current) C_GREEN else C_TXT, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist, color = C_SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            Text("+", color = C_TXT, fontSize = 26.sp, modifier = Modifier.clickable { menu = true }.padding(horizontal = 12.dp))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { Player.playNext(s); menu = false })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { Player.addToQueue(s); menu = false })
                Player.playlists.keys.sorted().forEach { n ->
                    DropdownMenuItem(text = { Text("Add to $n") }, onClick = { Player.addToPlaylist(n, s); menu = false })
                }
            }
        }
    }
}

@Composable
fun NowPlaying(modifier: Modifier, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val id = Player.current?.id
    var art by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id) {
        art = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29 && id != null) {
                    val u = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    ctx.contentResolver.loadThumbnail(u, android.util.Size(600, 600), null).asImageBitmap()
                } else null
            } catch (_: Throwable) {
                null
            }
        }
    }
    val rec = Manifest.permission.RECORD_AUDIO
    var micOk by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, rec) == PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micOk = it }
    LaunchedEffect(Unit) { if (!micOk) micLauncher.launch(rec) }
    LaunchedEffect(id, micOk, Player.playing) {
        if (micOk && Player.playing) {
            delay(500)
            val s = Player.sessionId()
            if (s != 0) Viz.start(s)
        }
    }
    LaunchedEffect(Unit) { while (true) { Viz.fade(); delay(50) } }
    DisposableEffect(Unit) { onDispose { Viz.stop() } }

    Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "‹ Back", color = C_TXT, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Start).clickable { onClose() }.padding(vertical = 6.dp)
        )
        val img = art
        Box(
            Modifier.size(190.dp)
                .graphicsLayer {
                    val s = 1f + (if (Viz.tick >= 0) Viz.level() else 0f) * 0.05f
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(24.dp)).background(C_PANEL),
            contentAlignment = Alignment.Center
        ) {
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("♪", fontSize = 64.sp, color = C_TEAL)
        }
        Text(
            Player.current?.title ?: "Nothing playing", color = C_TXT, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)
        )
        Text(Player.current?.artist ?: "", color = C_SUB, fontSize = 13.sp, maxLines = 1)
        Canvas(Modifier.fillMaxWidth().height(80.dp).padding(vertical = 6.dp)) {
            val t = Viz.tick
            val n = Viz.N
            val gap = 4.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val h = (Viz.bars[i] * size.height).coerceAtLeast(3.dp.toPx())
                drawRoundRect(C_GREEN, Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
        if (!micOk) {
            Text("Allow microphone access to see the visualizer. Nothing is recorded.", color = C_SUB, fontSize = 11.sp)
        }
        SeekBar()
        TransportRow()
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Shuffle " + if (PlayMode.shuffle) "ON" else "OFF", PlayMode.shuffle, Modifier.weight(1f)) {
                PlayMode.shuffle = !PlayMode.shuffle
            }
            Pill("Repeat: " + listOf("Off", "All", "One")[PlayMode.repeat], PlayMode.repeat != 0, Modifier.weight(1f)) {
                PlayMode.repeat = (PlayMode.repeat + 1) % 3
            }
        }
    }
}

@Composable
fun PlayerTab(modifier: Modifier) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(granted) { if (granted && Player.songs.isEmpty()) Player.load() }
    LaunchedEffect(Unit) { while (true) { Player.tick(); delay(500) } }
    var view by remember { mutableStateOf("All songs") }
    var viewOpen by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    Column(modifier) {
        if (!granted) {
            Pill("Allow music access", true, Modifier.fillMaxWidth()) { launcher.launch(perm) }
        } else if (full) {
            NowPlaying(Modifier.weight(1f)) { full = false }
        } else {
            Box {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp))
                        .border(1.dp, C_TEAL, RoundedCornerShape(22.dp)).clickable { viewOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(view, color = C_TXT, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = viewOpen, onDismissRequest = { viewOpen = false }) {
                    (listOf("All songs", "Queue") + Player.playlists.keys.sorted()).forEach { n ->
                        DropdownMenuItem(text = { Text(n) }, onClick = { view = n; viewOpen = false })
                    }
                    DropdownMenuItem(text = { Text("+ New playlist") }, onClick = { view = Player.newPlaylist(); viewOpen = false })
                }
            }
            val list: List<Song> = when (view) {
                "All songs" -> Player.songs
                "Queue" -> Player.queue
                else -> Player.playlistSongs(view)
            }
            LazyColumn(Modifier.weight(1f).padding(vertical = 6.dp)) {
                itemsIndexed(list) { i, s ->
                    SongRow(s, Player.current?.id == s.id) {
                        if (view == "Queue") Player.play(i) else Player.playList(list, i)
                    }
                }
            }
            Column(Modifier.fillMaxWidth()) {
                Text(
                    (Player.current?.title ?: "Nothing playing") + "  ›", color = C_TXT, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { full = true }
                )
                Text(Player.current?.artist ?: "", color = C_SUB, fontSize = 12.sp, maxLines = 1)
                SeekBar()
                TransportRow()
            }
        }
    }
}
