package app.openeq

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun inkToggleColors(): ToggleButtonColors = ToggleButtonDefaults.toggleButtonColors(
    containerColor = Ink.Surface,
    contentColor = Ink.Text,
    checkedContainerColor = Ink.Accent,
    checkedContentColor = Ink.OnAccent
)

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
            ToggleButton(
                checked = on,
                onCheckedChange = { onSelect(i) },
                modifier = Modifier.weight(1f).height(48.dp).semantics { role = Role.Tab },
                shapes = shapes,
                colors = inkToggleColors(),
                elevation = null,
                border = BorderStroke(1.dp, if (on) Ink.Accent else Ink.Track),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkToggle(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ToggleButton(
        checked = on,
        onCheckedChange = { onClick() },
        modifier = modifier.height(48.dp),
        shapes = ToggleButtonDefaults.shapes(
            shape = RoundedCornerShape(24.dp),
            pressedShape = RoundedCornerShape(12.dp),
            checkedShape = RoundedCornerShape(24.dp)
        ),
        colors = inkToggleColors(),
        elevation = null,
        border = BorderStroke(1.dp, if (on) Ink.Accent else Ink.Track),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkOnButton(on: Boolean, onClick: () -> Unit) {
    ToggleButton(
        checked = on,
        onCheckedChange = { onClick() },
        modifier = Modifier.size(52.dp),
        shapes = ToggleButtonDefaults.shapes(
            shape = CircleShape,
            pressedShape = RoundedCornerShape(16.dp),
            checkedShape = CircleShape
        ),
        colors = inkToggleColors(),
        elevation = null,
        border = BorderStroke(1.dp, if (on) Ink.Accent else Ink.Track),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(if (on) "ON" else "OFF", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkPresetButton(text: String, open: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
    ToggleButton(
        checked = open,
        onCheckedChange = onToggle,
        modifier = modifier.height(52.dp),
        shapes = ToggleButtonDefaults.shapes(
            shape = RoundedCornerShape(26.dp),
            pressedShape = RoundedCornerShape(14.dp),
            checkedShape = RoundedCornerShape(26.dp)
        ),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = Ink.Surface,
            contentColor = Ink.Text,
            checkedContainerColor = Ink.AccentSoft,
            checkedContentColor = Ink.Text
        ),
        elevation = null,
        border = BorderStroke(1.dp, Ink.AccentSoft),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
