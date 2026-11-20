package app.openeq

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val OpenEqScheme = darkColorScheme(
    primary = Color(0xFFA98BFF),
    onPrimary = Color(0xFF1B0F40),
    primaryContainer = Color(0xFF4A3D7A),
    onPrimaryContainer = Color(0xFFEDE8FF),
    secondary = Color(0xFFC9BFE8),
    onSecondary = Color(0xFF2A2340),
    secondaryContainer = Color(0xFF2E2A45),
    onSecondaryContainer = Color(0xFFE4DEFF),
    tertiary = Color(0xFFD9B4F0),
    onTertiary = Color(0xFF35184A),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDE8FF),
    surface = Color(0xFF0E0C14),
    onSurface = Color(0xFFEDE8FF),
    onSurfaceVariant = Color(0xFF9A93B8),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0E0C14),
    surfaceContainer = Color(0xFF15131D),
    surfaceContainerHigh = Color(0xFF211E2E),
    surfaceContainerHighest = Color(0xFF2B2740),
    outline = Color(0xFF6E6788),
    outlineVariant = Color(0xFF2B2740)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenEqTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = OpenEqScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp)
        ),
        content = content
    )
}
