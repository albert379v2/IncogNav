package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFD0BCFF),
    tertiary = Color(0xFF381E72),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onPrimary = Color(0xFF1C1B1F),
    onSecondary = Color(0xFFE6E1E5),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF65558F),
    tertiary = Color(0xFFEADDFF),
    background = Color(0xFFF4F3F7),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF1C1B1F),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = ThemeState.isDarkMode,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (ThemeState.isDarkMode) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
