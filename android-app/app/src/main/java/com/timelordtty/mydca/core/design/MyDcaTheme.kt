package com.timelordtty.mydca.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WealthBlue = Color(0xFF2F7DD1)
private val WealthMint = Color(0xFF23A978)
private val WealthAmber = Color(0xFFF59E0B)
private val Ink = Color(0xFF172033)

private val LightColors = lightColorScheme(
    primary = WealthBlue,
    secondary = WealthMint,
    tertiary = WealthAmber,
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF2FB),
    background = Color(0xFFF5F8FC),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onSurface = Ink,
    onBackground = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC7FF),
    secondary = Color(0xFF6EE7B7),
    tertiary = Color(0xFFFBC56A),
    surface = Color(0xFF172033),
    surfaceVariant = Color(0xFF23324A),
    background = Color(0xFF0E1524),
    onPrimary = Color(0xFF08233F),
    onSecondary = Color(0xFF062B1E),
    onSurface = Color(0xFFE7EEF8),
    onBackground = Color(0xFFE7EEF8),
)

@Composable
fun MyDcaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
