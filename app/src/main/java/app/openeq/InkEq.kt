package app.openeq

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
            val sw = (8f + 3f * press).dp.toPx()
            val inset = 13.dp.toPx()
            val tl = Offset(inset, inset)
            val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
            val rad = sz.width / 2
            val tc = t.coerceIn(0f, 1f)
            val c = center
            drawCircle(Ink.Surface)
            val n = 11
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val a = Math.toRadians((135f + 270f * f).toDouble())
                val dx = Math.cos(a).toFloat()
                val dy = Math.sin(a).toFloat()
                val r1 = rad + sw / 2 + 2.dp.toPx()
                val r2 = r1 + 3.dp.toPx()
                drawLine(
                    if (f <= tc + 0.001f) Ink.Accent else Ink.Track,
                    Offset(c.x + dx * r1, c.y + dy * r1),
                    Offset(c.x + dx * r2, c.y + dy * r2),
                    1.5.dp.toPx(), StrokeCap.Round
                )
            }
            drawArc(Ink.Track, 135f, 270f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            for (g in 3 downTo 1) {
                drawArc(
                    Ink.Accent.copy(alpha = (0.05f + 0.07f * press) * (4 - g)),
                    135f, 270f * tc, false, tl, sz,
                    style = Stroke(sw + g * 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawArc(
                Brush.linearGradient(
                    listOf(Ink.AccentDeep, Ink.Accent),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f)
                ),
                135f, 270f * tc, false, tl, sz,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            val innerR = rad - sw / 2 - 5.dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF312C47), Color(0xFF15131D)),
                    center = Offset(c.x - innerR * 0.3f, c.y - innerR * 0.4f),
                    radius = innerR * 1.5f
                ),
                innerR, c
            )
            drawCircle(Ink.Edge, innerR, c, style = Stroke(1.dp.toPx()))
            val ang = Math.toRadians((135f + 270f * tc).toDouble())
            val dx = Math.cos(ang).toFloat()
            val dy = Math.sin(ang).toFloat()
            drawLine(
                Ink.Accent,
                Offset(c.x + dx * innerR * 0.35f, c.y + dy * innerR * 0.35f),
                Offset(c.x + dx * innerR * 0.8f, c.y + dy * innerR * 0.8f),
                3.dp.toPx(), StrokeCap.Round
            )
            val tip = Offset(c.x + dx * rad, c.y + dy * rad)
            drawCircle(Ink.Accent.copy(alpha = 0.25f), sw, tip)
            drawCircle(Ink.AccentLight, sw * 0.38f, tip)
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
                Modifier.width(70.dp).fillMaxHeight()
                    .inkPanel(RoundedCornerShape(28.dp)).padding(vertical = 6.dp)
            ) {
                InkBandCol("Preamp", EqEngine.preamp, 70, false) { EqEngine.updatePreamp(it) }
            }
            Spacer(Modifier.width(6.dp))
            EqEngine.LABELS.forEachIndexed { i, l ->
                InkBandCol(l, EqEngine.gains[i], 54) { EqEngine.setGain(i, it) }
            }
        }
        Canvas(
            Modifier.padding(vertical = 10.dp).fillMaxWidth().height(72.dp)
                .inkPanel(RoundedCornerShape(32.dp))
        ) {
            val w = size.width
            val h = size.height
            fun yy(v: Float) = h / 2 - v / 12f * (h / 2 - 8.dp.toPx())
            fun xx(i: Int) = 14.dp.toPx() + (w - 28.dp.toPx()) * i / 9f
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            for (i in 0 until 10) {
                drawLine(Ink.Track.copy(alpha = 0.35f), Offset(xx(i), 0f), Offset(xx(i), h), 1.dp.toPx())
            }
            drawLine(Ink.Track.copy(alpha = 0.6f), Offset(0f, yy(6f)), Offset(w, yy(6f)), 1.dp.toPx(), pathEffect = dash)
            drawLine(Ink.Track.copy(alpha = 0.6f), Offset(0f, yy(-6f)), Offset(w, yy(-6f)), 1.dp.toPx(), pathEffect = dash)
            drawLine(Ink.Track, Offset(0f, h / 2), Offset(w, h / 2), 1.5.dp.toPx())
            val path = Path()
            path.moveTo(0f, yy(EqEngine.eff(0)))
            path.lineTo(xx(0), yy(EqEngine.eff(0)))
            for (i in 1 until 10) {
                val mx = (xx(i - 1) + xx(i)) / 2
                path.cubicTo(mx, yy(EqEngine.eff(i - 1)), mx, yy(EqEngine.eff(i)), xx(i), yy(EqEngine.eff(i)))
            }
            path.lineTo(w, yy(EqEngine.eff(9)))
            val fill = Path()
            fill.addPath(path)
            fill.lineTo(w, h)
            fill.lineTo(0f, h)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(Ink.Accent.copy(alpha = 0.32f), Color.Transparent)))
            val lineBrush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.07f to Ink.Accent,
                0.93f to Ink.Accent,
                1f to Color.Transparent,
                startX = 0f,
                endX = w
            )
            drawPath(path, Ink.Accent.copy(alpha = 0.10f), style = Stroke(11.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Ink.Accent.copy(alpha = 0.18f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, lineBrush, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
            for (i in 0 until 10) {
                val p = Offset(xx(i), yy(EqEngine.eff(i)))
                drawCircle(Ink.Accent.copy(alpha = 0.22f), 9.dp.toPx(), p)
                drawCircle(Ink.Accent, 4.5.dp.toPx(), p)
                drawCircle(Ink.AccentLight, 1.8.dp.toPx(), p)
            }
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
