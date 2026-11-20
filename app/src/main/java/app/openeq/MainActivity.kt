package app.openeq

import android.Manifest
import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

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
fun EqScreen(onStart: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier.fillMaxSize().background(BG).statusBarsPadding().navigationBarsPadding().padding(12.dp)
        ) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Box(
                    Modifier.width(70.dp).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                        .background(PANEL).padding(vertical = 6.dp)
                ) {
                    BandCol("Preamp", EqEngine.preamp, 70) { EqEngine.updatePreamp(it) }
                }
                Spacer(Modifier.width(6.dp))
                EqEngine.LABELS.forEachIndexed { i, l ->
                    BandCol(l, EqEngine.gains[i], 54) { EqEngine.setGain(i, it) }
                }
            }
            Canvas(
                Modifier.padding(vertical = 10.dp).fillMaxWidth().height(72.dp)
                    .clip(RoundedCornerShape(16.dp)).background(PANEL)
            ) {
                val w = size.width
                val h = size.height
                fun yy(v: Float) = h / 2 - v / 12f * (h / 2 - 8.dp.toPx())
                fun xx(i: Int) = w * i / (EqEngine.gains.size - 1)
                drawLine(TRACK, Offset(0f, h / 2), Offset(w, h / 2), 1.dp.toPx())
                val path = Path()
                path.moveTo(xx(0), yy(EqEngine.gains[0]))
                for (i in 1 until EqEngine.gains.size) {
                    val mx = (xx(i - 1) + xx(i)) / 2
                    path.cubicTo(mx, yy(EqEngine.gains[i - 1]), mx, yy(EqEngine.gains[i]), xx(i), yy(EqEngine.gains[i]))
                }
                drawPath(path, GREEN, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(if (EqEngine.enabled) TEAL else PANEL)
                        .border(1.dp, TEAL, CircleShape)
                        .clickable { EqEngine.toggle(); if (EqEngine.enabled) onStart() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (EqEngine.enabled) "ON" else "OFF", color = TXT, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                var open by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                            .border(1.dp, TEAL, RoundedCornerShape(26.dp))
                            .clickable { open = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(EqEngine.preset, color = TXT, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        EqEngine.names().forEach { n ->
                            DropdownMenuItem(text = { Text(n) }, onClick = { EqEngine.select(n); open = false })
                        }
                        DropdownMenuItem(text = { Text("+ New preset") }, onClick = { EqEngine.newPreset(); open = false })
                    }
                }
            }
            Text(
                "Turn ON, then play music. Works with apps that share their audio session.",
                color = TRACK, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
