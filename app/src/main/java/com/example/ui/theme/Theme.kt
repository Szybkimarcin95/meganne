package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CockpitColorScheme = darkColorScheme(
  primary = CyanHud,
  onPrimary = Color.Black,
  primaryContainer = CyanHudDim,
  onPrimaryContainer = CyanHud,
  secondary = AmberBose,
  onSecondary = Color.Black,
  secondaryContainer = AmberBoseDim,
  onSecondaryContainer = AmberBose,
  tertiary = DiagnosticGreen,
  onTertiary = Color.Black,
  background = CockpitBackground,
  onBackground = TextPrimary,
  surface = CockpitSurface,
  onSurface = TextPrimary,
  surfaceVariant = CockpitSurfaceVariant,
  onSurfaceVariant = TextSecondary,
  outline = CockpitBorder,
  error = WarningRed,
  onError = Color.White,
  errorContainer = WarningRedDim,
  onErrorContainer = WarningRed
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = CockpitColorScheme,
    typography = Typography,
    content = content
  )
}

