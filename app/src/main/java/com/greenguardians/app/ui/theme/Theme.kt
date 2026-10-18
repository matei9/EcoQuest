package com.greenguardians.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA1D84A),
    onPrimary = Color(0xFF19350C),
    primaryContainer = Color(0xFF26432E),
    onPrimaryContainer = Color(0xFFD9F3BE),
    secondary = Color(0xFF6DCCDB),
    background = Color(0xFF142127),
    surface = Color(0xFF19292F),
    surfaceVariant = Color(0xFF23363E),
    onSurfaceVariant = Color(0xFFBBCED0),
    outline = Color(0xFF5F7880),
    outlineVariant = Color(0xFF354C54),
    onBackground = Color(0xFFF0F5EB),
    onSurface = Color(0xFFF0F5EB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF397C27),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F1C9),
    onPrimaryContainer = Color(0xFF203F19),
    secondary = Color(0xFF237D8B),
    background = Color(0xFFF7F9F0),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF0E3),
    onSurface = Color(0xFF24372B),
    onBackground = Color(0xFF24372B),
    onSurfaceVariant = Color(0xFF52664F),
    outline = Color(0xFF788E72),
    outlineVariant = Color(0xFFD4DFCC),
)

/** darkMode null = follow system. */
@Composable
fun EcoQuestTheme(darkMode: Boolean? = null, monetColors: Boolean = false, content: @Composable () -> Unit) {
    val dark = darkMode ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (monetColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    val base = Typography()
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        typography = base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
        ),
        content = content,
    )
}
