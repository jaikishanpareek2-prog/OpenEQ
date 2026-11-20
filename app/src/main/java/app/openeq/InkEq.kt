package app.openeq

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InkKnob(label: String, text: String, t: Float, onT: (Float) -> Unit) {
    val latestT = rememberUpdatedState(t)
    val latest = rememberUpdatedState(onT)
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "inkKnobPress"
    )
    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(76.dp).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pressed = true
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
                    pressed = false
                }
            }
        ) {
            val sw = (9f + 3f * press).dp.toPx()
            val inset = 12.dp.toPx()
            val tl = Offset(inset, inset)
            val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
            val tc = t.coerceIn(0f, 1f)
            val rad = sz.width / 2
            drawCircle(Ink.Surface)
            drawArc(Ink.Track, 135f, 270f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(Ink.Accent, 135f, 270f * tc, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            drawCircle(Ink.SurfaceHigh, rad - sw / 2 - 4.dp.toPx())
            val ang = Math.toRadians((135f + 270f * tc).toDouble())
            val pos = Offset(center.x + rad * Math.cos(ang).toFloat(), center.y + rad * Math.sin(ang).toFloat())
            drawCircle(Ink.OnAccent, sw * 0.25f, pos)
        }
        Text(label, color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text, color = Ink.TextDim, fontSize = 12.sp)
    }
}

@Composable
fun InkPKnob(label: String, v: Float, a: Float, b: Float, unit: String, dec: Int, onV: (Float) -> Unit) {
    InkKnob(label, "%.${dec}f%s".format(v, unit), (v - a) / (b - a)) { t -> onV(a + t * (b - a)) }
}

@Composable
fun EqTab(modifier: Modifier, onStart: () -> Unit) {
    Column(modifier) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Box(
                Modifier.width(70.dp).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                    .background(Ink.Surface).padding(vertical = 6.dp)
            ) {
                InkBandCol("Preamp", EqEngine.preamp, 70) { EqEngine.updatePreamp(it) }
            }
            Spacer(Modifier.width(6.dp))
            EqEngine.LABELS.forEachIndexed { i, l ->
                InkBandCol(l, EqEngine.gains[i], 54) { EqEngine.setGain(i, it) }
            }
        }
        Canvas(
            Modifier.padding(vertical = 10.dp).fillMaxWidth().height(72.dp)
                .clip(RoundedCornerShape(32.dp)).background(Ink.Surface)
        ) {
            val w = size.width
            val h = size.height
            fun yy(v: Float) = h / 2 - v / 12f * (h / 2 - 8.dp.toPx())
            fun xx(i: Int) = 14.dp.toPx() + (w - 28.dp.toPx()) * i / 9f
            drawLine(Ink.Track, Offset(0f, h / 2), Offset(w, h / 2), 1.dp.toPx())
            val path = Path()
            path.moveTo(xx(0), yy(EqEngine.eff(0)))
            for (i in 1 until 10) {
                val mx = (xx(i - 1) + xx(i)) / 2
                path.cubicTo(mx, yy(EqEngine.eff(i - 1)), mx, yy(EqEngine.eff(i)), xx(i), yy(EqEngine.eff(i)))
            }
            val fill = Path()
            fill.addPath(path)
            fill.lineTo(xx(9), h)
            fill.lineTo(xx(0), h)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(Ink.Accent.copy(alpha = 0.30f), Color.Transparent)))
            drawPath(path, Ink.Accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            for (i in 0 until 10) drawCircle(Ink.Accent, 4.5.dp.toPx(), Offset(xx(i), yy(EqEngine.eff(i))))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InkOnButton(EqEngine.enabled) { EqEngine.toggle(); if (EqEngine.enabled) onStart() }
            Spacer(Modifier.width(12.dp))
            OutputPill()
            Spacer(Modifier.width(8.dp))
            var open by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                InkPresetButton(EqEngine.preset, open, Modifier.fillMaxWidth()) { open = it }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    EqEngine.names().forEach { n ->
                        DropdownMenuItem(text = { Text(n) }, onClick = { EqEngine.select(n); open = false })
                    }
                    DropdownMenuItem(text = { Text("+ New preset") }, onClick = { EqEngine.newPreset(); open = false })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            InkPKnob("Bass", EqEngine.bass, -100f, 100f, "%", 0) { EqEngine.bass = Math.round(it).toFloat(); EqEngine.changed() }
            InkPKnob("Treble", EqEngine.treble, -100f, 100f, "%", 0) { EqEngine.treble = Math.round(it).toFloat(); EqEngine.changed() }
        }
    }
}
