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
 * ASH palette: cool ash-grey neutrals, a single ember accent, and semantic status colours.
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

private val Ember = Color(0xFFC2581C)
private val EmberDark = Color(0xFFF0935A)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE3D4),
    onPrimaryContainer = Color(0xFF4A1C02),
    secondary = Color(0xFF4F5862),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E7EB),
    onSecondaryContainer = Color(0xFF1B1F23),
    tertiary = Color(0xFF2F6F8F),
    background = Color(0xFFF4F5F6),
    onBackground = Color(0xFF15181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15181B),
    surfaceVariant = Color(0xFFECEEF0),
    onSurfaceVariant = Color(0xFF5A626A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFA),
    surfaceContainer = Color(0xFFF1F2F4),
    surfaceContainerHigh = Color(0xFFEBEDEF),
    surfaceContainerHighest = Color(0xFFE5E7EA),
    outline = Color(0xFFC3C8CD),
    outlineVariant = Color(0xFFDDE1E4),
    error = Color(0xFFB42318),
    onError = Color.White,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = EmberDark,
    onPrimary = Color(0xFF3A1600),
    primaryContainer = Color(0xFF4D2410),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFB9C1C9),
    onSecondary = Color(0xFF242A30),
    secondaryContainer = Color(0xFF2C3238),
    onSecondaryContainer = Color(0xFFDDE3E9),
    tertiary = Color(0xFF8CC7E6),
    background = Color(0xFF0F1113),
    onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF181B1E),
    onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF22262A),
    onSurfaceVariant = Color(0xFF9EA6AE),
    surfaceContainerLowest = Color(0xFF0B0D0E),
    surfaceContainerLow = Color(0xFF15181B),
    surfaceContainer = Color(0xFF1C1F23),
    surfaceContainerHigh = Color(0xFF23272B),
    surfaceContainerHighest = Color(0xFF2B2F34),
    outline = Color(0xFF454C53),
    outlineVariant = Color(0xFF30353A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A06),
)

private val LightStatus = AshStatusColors(
    urgent = Color(0xFFB42318), urgentContainer = Color(0xFFFDECEA),
    soon = Color(0xFF8F5400), soonContainer = Color(0xFFFFF1D6),
    ok = Color(0xFF1C7340), okContainer = Color(0xFFE3F3E8),
    info = Color(0xFF2F6F8F), infoContainer = Color(0xFFE2F0F7),
    groupedBackground = Color(0xFFF4F5F6), separator = Color(0xFFE3E6E9),
)

private val DarkStatus = AshStatusColors(
    urgent = Color(0xFFFF8A80), urgentContainer = Color(0xFF3A1917),
    soon = Color(0xFFF5C062), soonContainer = Color(0xFF33280F),
    ok = Color(0xFF7BD69A), okContainer = Color(0xFF152E1E),
    info = Color(0xFF8CC7E6), infoContainer = Color(0xFF14262F),
    groupedBackground = Color(0xFF0F1113), separator = Color(0xFF2A2E33),
)

val LocalAshStatus = staticCompositionLocalOf { LightStatus }

private val AshShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
