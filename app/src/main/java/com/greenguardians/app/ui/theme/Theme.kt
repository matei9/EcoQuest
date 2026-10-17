package com.greenguardians.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF10B981),
    background = Color(0xFF0D1117),
    surface = Color(0xFF121820),
    surfaceVariant = Color(0xFF1A222D),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF059669),
    background = Color(0xFFF6FAF7),
    surface = Color.White,
)

/** darkMode null = follow system. */
@Composable
fun GreenGuardiansTheme(darkMode: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkMode ?: isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
