package app.ash.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.ash.R

private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Manrope (bundled, variable) for display and headings; the system face for body text. */
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

/**
 * One family everywhere, on the iOS Dynamic Type scale (sizes in sp follow points):
 * large title 34 · title1 28 · title2 22 · title3 20 · headline 17 semibold · body 17 ·
 * callout 16 · subheadline 15 · footnote 13 · caption 12. Large sizes get slightly tighter tracking.
 */
private val F = Manrope

val AshTypography = Typography(
    displayLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = (-0.025).em),
    displayMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.025).em),
    displaySmall = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.02).em),
    headlineLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.02).em),
    // Expanded large title in the top bar.
    headlineMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 39.sp, letterSpacing = (-0.02).em),
    headlineSmall = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    // Collapsed top-bar title and section titles.
    titleLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.005).em),
    titleSmall = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.005).em),
    bodyMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em),
)
