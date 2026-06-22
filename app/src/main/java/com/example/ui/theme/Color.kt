package com.example.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object ThemeState {
    var isDarkMode by mutableStateOf(true)
}

// Elegant Adaptive Design Theme Colors
val PremiumVoid: Color
    get() = if (ThemeState.isDarkMode) Color(0xFF1C1B1F) else Color(0xFFF4F3F7)

val CardBackground: Color
    get() = if (ThemeState.isDarkMode) Color(0xFF2B2930) else Color(0xFFFFFFFF)

val LightAccents: Color
    get() = if (ThemeState.isDarkMode) Color(0xFF36343B) else Color(0xFFE6E5EA)

val BrightCyan: Color
    get() = if (ThemeState.isDarkMode) Color(0xFFD0BCFF) else Color(0xFF6750A4)

val TextOffWhite: Color
    get() = if (ThemeState.isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)

val TextMuted: Color
    get() = if (ThemeState.isDarkMode) Color(0xFF938F99) else Color(0xFF5E5A66)

val AccentTeal: Color
    get() = if (ThemeState.isDarkMode) Color(0xFFD0BCFF) else Color(0xFF65558F)

val AccentElectric: Color
    get() = if (ThemeState.isDarkMode) Color(0xFF381E72) else Color(0xFFEADDFF)

val DangerRed: Color
    get() = if (ThemeState.isDarkMode) Color(0xFFF2B8B5) else Color(0xFFB3261E)

val GlowGreen: Color
    get() = if (ThemeState.isDarkMode) Color(0xFF81C784) else Color(0xFF2E7D32)

val ElegantLilac: Color
    get() = if (ThemeState.isDarkMode) Color(0xFFEADDFF) else Color(0xFF380087)

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

