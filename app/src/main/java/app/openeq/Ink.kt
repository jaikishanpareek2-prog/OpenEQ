package app.openeq

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val OnAccent = Color(0xFF1B0F40)
    val Text = Color(0xFFEDE8FF)
    val TextDim = Color(0xFF9A93B8)
    val Track = Color(0xFF2B2740)
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
        drawLine(Ink.Track, Offset(cx, 0f), Offset(cx, size.height), 4.dp.toPx())
        drawLine(Ink.Accent, Offset(cx, y(0f)), Offset(cx, y(value)), 5.dp.toPx(), StrokeCap.Round)
        val hw = 28.dp.toPx()
        val hh = 44.dp.toPx()
        val tl = Offset(cx - hw / 2, y(value) - hh / 2)
        drawRoundRect(Ink.SurfaceHigh, tl, Size(hw, hh), CornerRadius(hw / 2))
        drawRoundRect(Ink.Accent, tl, Size(hw, hh), CornerRadius(hw / 2), style = Stroke(2.dp.toPx()))
        drawLine(Ink.Accent, Offset(cx - 7.dp.toPx(), y(value)), Offset(cx + 7.dp.toPx(), y(value)), 3.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun InkBandCol(label: String, value: Float, width: Int, onChange: (Float) -> Unit) {
    Column(Modifier.width(width.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        InkSlider(value, -12f, 12f, onChange, Modifier.weight(1f).fillMaxWidth())
        Text(label, color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("%.1f".format(value), color = Ink.TextDim, fontSize = 13.sp)
    }
}
