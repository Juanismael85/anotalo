package com.voicenotes.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.voicenotes.app.R

private val DarkColors = darkColorScheme(
  primary = Color(0xFFB9A3FF),
  onPrimary = Color(0xFF221046),
  primaryContainer = Color(0xFF352563),
  onPrimaryContainer = Color(0xFFE9DEFF),
  secondary = Color(0xFF5FE3D8),
  onSecondary = Color(0xFF00332F),
  secondaryContainer = Color(0xFF12443F),
  onSecondaryContainer = Color(0xFFB6FFF7),
  background = Color(0xFF0D0D11),
  onBackground = Color(0xFFE8E6F0),
  surface = Color(0xFF15141B),
  onSurface = Color(0xFFE8E6F0),
  surfaceVariant = Color(0xFF232030),
  onSurfaceVariant = Color(0xFFB7B3C6),
  outline = Color(0xFF4B4659),
  outlineVariant = Color(0xFF322E3E),
  error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
  primary = Color(0xFF6347C7),
  onPrimary = Color.White,
  secondary = Color(0xFF00696A),
  background = Color(0xFFFDFBFF),
  surface = Color(0xFFFDFBFF),
)

/** Bundled static font: some MIUI font themes replace `sans-serif` with a serif face. */
private val AppFontFamily = FontFamily(Font(R.font.roboto_regular, FontWeight.Normal))

private fun appTypography(): Typography {
  val base = Typography()
  val sans = AppFontFamily
  return Typography(
    displayLarge = base.displayLarge.copy(fontFamily = sans),
    displayMedium = base.displayMedium.copy(fontFamily = sans),
    displaySmall = base.displaySmall.copy(fontFamily = sans),
    headlineLarge = base.headlineLarge.copy(fontFamily = sans),
    headlineMedium = base.headlineMedium.copy(fontFamily = sans),
    headlineSmall = base.headlineSmall.copy(fontFamily = sans),
    titleLarge = base.titleLarge.copy(fontFamily = sans),
    titleMedium = base.titleMedium.copy(fontFamily = sans),
    titleSmall = base.titleSmall.copy(fontFamily = sans),
    bodyLarge = base.bodyLarge.copy(fontFamily = sans),
    bodyMedium = base.bodyMedium.copy(fontFamily = sans),
    bodySmall = base.bodySmall.copy(fontFamily = sans),
    labelLarge = base.labelLarge.copy(fontFamily = sans),
    labelMedium = base.labelMedium.copy(fontFamily = sans),
    labelSmall = base.labelSmall.copy(fontFamily = sans),
  )
}

private val AppTypography = appTypography()

@Composable
fun VoiceNotesTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = AppTypography,
    content = content,
  )
}
