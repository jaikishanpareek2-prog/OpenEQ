package app.openeq

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Ink {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF15131D)
    val SurfaceHigh = Color(0xFF211E2E)
    val Accent = Color(0xFFA98BFF)
    val AccentSoft = Color(0xFF4A3D7A)
    val AccentDeep = Color(0xFF6B4FD8)
    val AccentLight = Color(0xFFDCD0FF)
    val OnAccent = Color(0xFF1B0F40)
    val Text = Color(0xFFEDE8FF)
    val TextDim = Color(0xFF9A93B8)
    val Track = Color(0xFF2B2740)
    val Edge = Color(0xFF3A3552)
    val PanelTop = Color(0xFF1D1A29)
    val PanelBottom = Color(0xFF0E0D14)
}

fun Modifier.inkPanel(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Ink.PanelTop, Ink.PanelBottom)))
    .border(1.dp, Brush.verticalGradient(listOf(Ink.Edge, Ink.Track.copy(alpha = 0.35f))), shape)

private fun DrawScope.glowRound(x: Float, y: Float, w: Float, h: Float, rad: Float, c: Color, a: Float) {
    for (k in 3 downTo 1) {
        val e = k * 2.5.dp.toPx()
        drawRoundRect(
            c.copy(alpha = (0.07f * a * (4 - k)).coerceIn(0f, 1f)),
            Offset(x - e, y - e),
            Size(w + 2 * e, h + 2 * e),
            CornerRadius(rad + e)
        )
    }
}

@Composable
fun InkPill(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.height(48.dp).clip(shape)
            .background(if (on) Ink.Accent else Ink.Surface)
            .border(1.dp, if (on) Ink.Accent else Ink.Track, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (on) Ink.OnAccent else Ink.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun InkSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val latest = rememberUpdatedState(onChange)
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "inkSliderPress"
    )
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
                pressed = true
                latest.value(fromY(down.position.y))
                do {
                    val ev = awaitPointerEvent()
                    val p = ev.changes.first()
                    if (p.pressed) {
                        latest.value(fromY(p.position.y))
                        p.consume()
                    }
                } while (ev.changes.any { it.pressed })
                pressed = false
            }
        }
    ) {
        val pad = 30.dp.toPx()
        val cx = size.width / 2
        fun y(v: Float) = pad + (max - v) / (max - min) * (size.height - 2 * pad)
        val tw = (14f + 3f * press).dp.toPx()
        val x0 = cx - tw / 2
        val r = CornerRadius(tw / 2)
        val hh = (9f - 3f * press).dp.toPx()
        val hw = (36f + 6f * press).dp.toPx()
        val half = hh / 2 + 5.dp.toPx()
        val yv = y(value)
        val y0 = y(0f)
        val tick = 5.dp.toPx()
        val tg = 3.dp.toPx()
        drawLine(Ink.TextDim, Offset(x0 - tg - tick, y0), Offset(x0 - tg, y0), 2.dp.toPx(), StrokeCap.Round)
        drawLine(Ink.TextDim, Offset(x0 + tw + tg, y0), Offset(x0 + tw + tg + tick, y0), 2.dp.toPx(), StrokeCap.Round)
        val groove = Brush.horizontalGradient(
            listOf(Color(0xFF171523), Ink.Track, Color(0xFF171523)),
            startX = x0,
            endX = x0 + tw
        )
        val topEnd = (yv - half).coerceAtLeast(0f)
        if (topEnd > 0f) drawRoundRect(groove, Offset(x0, 0f), Size(tw, topEnd), r)
        val botStart = (yv + half).coerceAtMost(size.height)
        if (botStart < size.height) drawRoundRect(groove, Offset(x0, botStart), Size(tw, size.height - botStart), r)
        if (value > 0f) {
            val s = yv + half
            if (y0 > s) {
                glowRound(x0, s, tw, y0 - s, tw / 2, Ink.Accent, 1f + press)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Ink.Accent, Ink.AccentDeep), startY = s, endY = y0),
                    Offset(x0, s), Size(tw, y0 - s), r
                )
            }
        } else if (value < 0f) {
            val e = yv - half
            if (e > y0) {
                glowRound(x0, y0, tw, e - y0, tw / 2, Ink.Accent, 1f + press)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Ink.AccentDeep, Ink.Accent), startY = y0, endY = e),
                    Offset(x0, y0), Size(tw, e - y0), r
                )
            }
        }
        glowRound(cx - hw / 2, yv - hh / 2, hw, hh, hh / 2, Ink.Accent, 1f + press)
        drawRoundRect(
            Brush.verticalGradient(listOf(Ink.AccentLight, Ink.Accent), startY = yv - hh / 2, endY = yv + hh / 2),
            Offset(cx - hw / 2, yv - hh / 2), Size(hw, hh), CornerRadius(hh / 2)
        )
    }
}

@Composable
fun InkBandCol(label: String, value: Float, width: Int, panel: Boolean = true, onChange: (Float) -> Unit) {
    val base = Modifier.width(width.dp).fillMaxHeight()
    Column(
        (if (panel) base.padding(horizontal = 2.dp).inkPanel(RoundedCornerShape(26.dp)) else base).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        InkSlider(value, -12f, 12f, onChange, Modifier.weight(1f).fillMaxWidth())
        Text(label, color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("%.1f".format(value), color = if (value != 0f) Ink.Accent else Ink.TextDim, fontSize = 13.sp)
    }
}
