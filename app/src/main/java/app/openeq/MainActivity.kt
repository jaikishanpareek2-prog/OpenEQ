package app.openeq

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.abs

private val BG = Color(0xFF121C2B)
private val PANEL = Color(0xFF00222A)
private val TEAL = Color(0xFF0F6C7C)
private val HANDLE = Color(0xFF002027)
private val GREEN = Color(0xFF3CFF14)
private val TXT = Color(0xFF8FEBFF)
private val TRACK = Color(0xFF3A4350)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EqEngine.init(this)
        Player.init(this)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (EqEngine.enabled) startEq()
        setContent { EqScreen { startEq() } }
    }

    private fun startEq() {
        ContextCompat.startForegroundService(this, Intent(this, EqService::class.java))
    }
}

@Composable
fun VSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val latest = rememberUpdatedState(onChange)
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val pad = 30.dp.toPx()
                fun fromY(y: Float): Float {
                    val t = ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                    return Math.round((max - t * (max - min)) * 2f) / 2f
                }
                val down = awaitFirstDown()
                down.consume()
                latest.value(fromY(down.position.y))
                do {
                    val ev = awaitPointerEvent()
                    val p = ev.changes.first()
                    if (p.pressed) {
                        latest.value(fromY(p.position.y))
                        p.consume()
                    }
                } while (ev.changes.any { it.pressed })
            }
        }
    ) {
        val pad = 30.dp.toPx()
        val cx = size.width / 2
        fun y(v: Float) = pad + (max - v) / (max - min) * (size.height - 2 * pad)
        drawLine(TRACK, Offset(cx, 0f), Offset(cx, size.height), 3.dp.toPx())
        drawLine(GREEN, Offset(cx, y(0f)), Offset(cx, y(value)), 4.dp.toPx(), StrokeCap.Round)
        val hw = 26.dp.toPx()
        val hh = 44.dp.toPx()
        val tl = Offset(cx - hw / 2, y(value) - hh / 2)
        drawRoundRect(HANDLE, tl, Size(hw, hh), CornerRadius(hw / 2))
        drawRoundRect(TEAL, tl, Size(hw, hh), CornerRadius(hw / 2), style = Stroke(2.dp.toPx()))
        drawLine(TEAL, Offset(cx - 7.dp.toPx(), y(value)), Offset(cx + 7.dp.toPx(), y(value)), 3.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun BandCol(label: String, value: Float, width: Int, onChange: (Float) -> Unit) {
    Column(Modifier.width(width.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        VSlider(value, -12f, 12f, onChange, Modifier.weight(1f).fillMaxWidth())
        Text(label, color = TXT, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("%.1f".format(value), color = TXT, fontSize = 13.sp)
    }
}

@Composable
fun Knob(label: String, text: String, t: Float, onT: (Float) -> Unit) {
    val latestT = rememberUpdatedState(t)
    val latest = rememberUpdatedState(onT)
    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(76.dp).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var cur = latestT.value
                    var lastY = down.position.y
                    do {
                        val ev = awaitPointerEvent()
                        val p = ev.changes.first()
                        if (p.pressed) {
                            cur = (cur + (lastY - p.position.y) / 300.dp.toPx()).coerceIn(0f, 1f)
                            lastY = p.position.y
                            latest.value(cur)
                            p.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
        ) {
            val sw = 7.dp.toPx()
            val tl = Offset(sw, sw)
            val sz = Size(size.width - 2 * sw, size.height - 2 * sw)
            drawCircle(PANEL)
            drawArc(TRACK, 135f, 270f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(GREEN, 135f, 270f * t.coerceIn(0f, 1f), false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(label, color = TXT, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text, color = TXT, fontSize = 12.sp)
    }
}

@Composable
fun PKnob(label: String, v: Float, a: Float, b: Float, unit: String, dec: Int, onV: (Float) -> Unit) {
    Knob(label, "%.${dec}f%s".format(v, unit), (v - a) / (b - a)) { t -> onV(a + t * (b - a)) }
}

@Composable
fun Pill(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(if (on) TEAL else PANEL)
            .border(1.dp, TEAL, RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TXT, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DynTab(modifier: Modifier) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val vmax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    var vol by remember { mutableStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("Limiter " + if (EqEngine.limiterOn) "ON" else "OFF", EqEngine.limiterOn, Modifier.weight(1f)) {
                EqEngine.limiterOn = !EqEngine.limiterOn; EqEngine.changed()
            }
            Pill("Compressor " + if (EqEngine.compOn) "ON" else "OFF", EqEngine.compOn, Modifier.weight(1f)) {
                EqEngine.compOn = !EqEngine.compOn; EqEngine.changed()
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PKnob("Attack", EqEngine.cAttack, 1f, 200f, " ms", 0) { EqEngine.cAttack = Math.round(it).toFloat(); EqEngine.changed() }
            PKnob("Release", EqEngine.cRelease, 10f, 1000f, " ms", 0) { EqEngine.cRelease = Math.round(it).toFloat(); EqEngine.changed() }
            PKnob("Ratio", EqEngine.cRatio, 1f, 20f, ":1", 1) { EqEngine.cRatio = Math.round(it * 10f) / 10f; EqEngine.changed() }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PKnob("Threshold", EqEngine.cThresh, -60f, 0f, " dB", 0) { EqEngine.cThresh = Math.round(it).toFloat(); EqEngine.changed() }
            PKnob("Gain", EqEngine.cGain, 0f, 12f, " dB", 1) { EqEngine.cGain = Math.round(it * 10f) / 10f; EqEngine.changed() }
            PKnob("Balance", EqEngine.balance, -1f, 1f, "", 2) {
                EqEngine.balance = if (abs(it) < 0.05f) 0f else Math.round(it * 100f) / 100f
                EqEngine.changed()
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Knob("Volume", "${(vol / vmax * 100).toInt()}%", vol / vmax) { t ->
                val n = Math.round(t * vmax)
                vol = n.toFloat()
                am.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0)
            }
        }
    }
}

@Composable
fun EqScreen(onStart: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    OpenEqTheme {
        Column(
            Modifier.fillMaxSize().background(Ink.Bg).statusBarsPadding().navigationBarsPadding().padding(12.dp)
        ) {
            when (tab) { 0 -> EqTab(Modifier.weight(1f), onStart); 1 -> DynTab(Modifier.weight(1f)); 2 -> PlayerTab(Modifier.weight(1f)); else -> PeqTab(Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkConnectedTabs(listOf("Equalizer", "Dynamics", "Player", androidx.compose.ui.res.stringResource(R.string.b6_tab_param)), tab, Modifier.weight(1f)) { tab = it }
            }
        }
    }
}
