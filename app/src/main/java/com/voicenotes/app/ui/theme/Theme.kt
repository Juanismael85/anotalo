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

/** Pure black for OLED panels: true #000 background, near-white text. */
private val AmoledColors = darkColorScheme(
  primary = Color(0xFFC6B4FF),
  onPrimary = Color(0xFF1C0B44),
  primaryContainer = Color(0xFF2C1D57),
  onPrimaryContainer = Color(0xFFEDE3FF),
  secondary = Color(0xFF67EDE2),
  onSecondary = Color(0xFF00322E),
  secondaryContainer = Color(0xFF0F3D39),
  onSecondaryContainer = Color(0xFFBEFFF8),
  background = Color(0xFF000000),
  onBackground = Color(0xFFEDEBF5),
  surface = Color(0xFF000000),
  onSurface = Color(0xFFEDEBF5),
  surfaceVariant = Color(0xFF16151C),
  onSurfaceVariant = Color(0xFFB0ACC0),
  outline = Color(0xFF423E4F),
  outlineVariant = Color(0xFF2A2732),
  error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
  primary = Color(0xFF5B3FD1),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFE6DEFF),
  onPrimaryContainer = Color(0xFF1B0F45),
  secondary = Color(0xFF00696A),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFB6F2EE),
  onSecondaryContainer = Color(0xFF00201F),
  background = Color(0xFFFDFBFF),
  onBackground = Color(0xFF1B1B1F),
  surface = Color(0xFFFFFFFF),
  onSurface = Color(0xFF1B1B1F),
  surfaceVariant = Color(0xFFE9E4F2),
  onSurfaceVariant = Color(0xFF4A4658),
  outline = Color(0xFF7A7588),
  outlineVariant = Color(0xFFCFC9DA),
  error = Color(0xFFB3261E),
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

fun colorSchemeFor(mode: ThemeMode) = when (mode) {
  ThemeMode.LIGHT -> LightColors
  ThemeMode.DARK -> DarkColors
  ThemeMode.AMOLED -> AmoledColors
}

@Composable
fun VoiceNotesTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = colorSchemeFor(mode),
    typography = AppTypography,
    content = content,
  )
}
