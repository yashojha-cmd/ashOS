package app.ash.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.ash.data.repository.ThemeMode

/**
 * ashOS palette, modelled on iOS system colours.
 * Status is always paired with an icon and a text label, never colour alone.
 */
@Immutable
data class AshStatusColors(
    val urgent: Color,
    val urgentContainer: Color,
    val soon: Color,
    val soonContainer: Color,
    val ok: Color,
    val okContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val groupedBackground: Color,
    val separator: Color,
)

// iOS-style system palette: neutral greys, true black in dark mode, one vivid accent (indigo),
// and the familiar red / orange / green / blue status hues (darkened slightly where used as text).
private val Accent = Color(0xFF5856D6)
private val AccentDark = Color(0xFF7D7AFF)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E6FB),
    onPrimaryContainer = Color(0xFF1E1C6B),
    secondary = Color(0xFF636366),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF007AFF),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE9E9EE),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color.White,
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFD70015),
    onError = Color.White,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B2A5C),
    onPrimaryContainer = Color(0xFFE2E1FF),
    secondary = Color(0xFFAEAEB2),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFE5E5EA),
    tertiary = Color(0xFF0A84FF),
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    outline = Color(0xFF545458),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
    onError = Color.White,
)

private val LightStatus = AshStatusColors(
    urgent = Color(0xFFD70015), urgentContainer = Color(0xFFFFE5E3),
    soon = Color(0xFFC93400), soonContainer = Color(0xFFFFEEDB),
    ok = Color(0xFF248A3D), okContainer = Color(0xFFE2F6E7),
    info = Color(0xFF0071E3), infoContainer = Color(0xFFE1EEFF),
    groupedBackground = Color(0xFFF2F2F7), separator = Color(0xFFC6C6C8),
)

private val DarkStatus = AshStatusColors(
    urgent = Color(0xFFFF453A), urgentContainer = Color(0xFF3D1513),
    soon = Color(0xFFFF9F0A), soonContainer = Color(0xFF3A2606),
    ok = Color(0xFF30D158), okContainer = Color(0xFF0F2E18),
    info = Color(0xFF0A84FF), infoContainer = Color(0xFF0B2440),
    groupedBackground = Color(0xFF000000), separator = Color(0xFF38383A),
)

/** iOS system colours for icon tiles (Settings-style): white glyph on a solid rounded square. */
val TilePalette = listOf(
    Color(0xFF007AFF), // blue
    Color(0xFF34C759), // green
    Color(0xFFFF9500), // orange
    Color(0xFFFF3B30), // red
    Color(0xFF5856D6), // indigo
    Color(0xFFAF52DE), // purple
    Color(0xFFFF2D55), // pink
    Color(0xFF32ADE6), // cyan
    Color(0xFF00C7BE), // mint
    Color(0xFF8E8E93), // grey
)

val LocalAshStatus = staticCompositionLocalOf { LightStatus }

private val AshShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun AshTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalAshStatus provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AshTypography,
            shapes = AshShapes,
            content = content,
        )
    }
}

object AshTheme {
    val status: AshStatusColors @Composable get() = LocalAshStatus.current
}

/** The app-icon background, used wherever the logo appears so it always matches the launcher. */
val BrandGraphite = Color(0xFF1B1E22)
