package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BrightCyan,
    secondary = AccentTeal,
    tertiary = AccentElectric,
    background = PremiumVoid,
    surface = PremiumVoid,
    onPrimary = PremiumVoid,
    onSecondary = TextOffWhite,
    onBackground = TextOffWhite,
    onSurface = TextOffWhite
  )

private val LightColorScheme = DarkColorScheme // Keep it consistently dark for the incognito browser aura

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force it dark for premium incognito theme
  dynamicColor: Boolean = false, // Preserve our beautiful crafted colors
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
