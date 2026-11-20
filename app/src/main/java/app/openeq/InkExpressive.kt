package app.openeq

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun inkToggleColors(): ToggleButtonColors = ToggleButtonDefaults.colors(
    containerColor = Ink.Surface,
    contentColor = Ink.Text,
    checkedContainerColor = Ink.Accent,
    checkedContentColor = Ink.OnAccent
)

private fun inkEdge(on: Boolean): BorderStroke = BorderStroke(
    1.dp,
    if (on) Brush.verticalGradient(listOf(Ink.AccentLight, Ink.Accent))
    else Brush.verticalGradient(listOf(Ink.Edge, Ink.Track))
)

private fun Modifier.inkSheen(on: Boolean): Modifier = this.drawBehind {
    val top = if (on) 0.32f else 0.08f
    val bottom = if (on) Ink.AccentDeep.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.30f)
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = top),
            0.5f to Color.Transparent,
            1f to bottom
        )
    )
}

private fun Modifier.inkGlow(a: Float, corner: Dp): Modifier = this.drawBehind {
    if (a > 0.01f) {
        val r = corner.toPx()
        for (k in 3 downTo 1) {
            val e = k * 2.5.dp.toPx()
            drawRoundRect(
                Ink.Accent.copy(alpha = (0.06f * a * (4 - k)).coerceIn(0f, 1f)),
                Offset(-e, -e),
                Size(size.width + 2 * e, size.height + 2 * e),
                CornerRadius(r + e)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkConnectedTabs(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        labels.forEachIndexed { i, label ->
            val shapes = when (i) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val on = selected == i
            val glow by animateFloatAsState(
                if (on) 1f else 0f,
                spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "inkTabGlow"
            )
            ToggleButton(
                checked = on,
                onCheckedChange = { onSelect(i) },
                modifier = Modifier.weight(1f).height(48.dp).inkGlow(glow, 24.dp).semantics { role = Role.Tab },
                shapes = shapes,
                colors = inkToggleColors(),
                border = inkEdge(on),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(Modifier.fillMaxSize().inkSheen(on), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkToggle(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val glow by animateFloatAsState(
        if (on) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "inkToggleGlow"
    )
    ToggleButton(
        checked = on,
        onCheckedChange = { onClick() },
        modifier = modifier.height(48.dp).inkGlow(glow, 24.dp),
        shapes = ToggleButtonShapes(
            shape = RoundedCornerShape(24.dp),
            pressedShape = RoundedCornerShape(12.dp),
            checkedShape = RoundedCornerShape(24.dp)
        ),
        colors = inkToggleColors(),
        border = inkEdge(on),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(Modifier.fillMaxSize().inkSheen(on), contentAlignment = Alignment.Center) {
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkOnButton(on: Boolean, onClick: () -> Unit) {
    val glow by animateFloatAsState(
        if (on) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "inkOnGlow"
    )
    ToggleButton(
        checked = on,
        onCheckedChange = { onClick() },
        modifier = Modifier.size(52.dp).inkGlow(glow, 26.dp),
        shapes = ToggleButtonShapes(
            shape = CircleShape,
            pressedShape = RoundedCornerShape(16.dp),
            checkedShape = CircleShape
        ),
        colors = inkToggleColors(),
        border = inkEdge(on),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(Modifier.fillMaxSize().inkSheen(on), contentAlignment = Alignment.Center) {
            Text(if (on) "ON" else "OFF", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkPresetButton(text: String, open: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
    val glow by animateFloatAsState(
        if (open) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "inkPresetGlow"
    )
    ToggleButton(
        checked = open,
        onCheckedChange = onToggle,
        modifier = modifier.height(52.dp).inkGlow(glow * 0.7f, 26.dp),
        shapes = ToggleButtonShapes(
            shape = RoundedCornerShape(26.dp),
            pressedShape = RoundedCornerShape(14.dp),
            checkedShape = RoundedCornerShape(26.dp)
        ),
        colors = ToggleButtonDefaults.colors(
            containerColor = Ink.Surface,
            contentColor = Ink.Text,
            checkedContainerColor = Ink.AccentSoft,
            checkedContentColor = Ink.Text
        ),
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Ink.Edge, Ink.AccentSoft))),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(Modifier.fillMaxSize().inkSheen(open), contentAlignment = Alignment.Center) {
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
