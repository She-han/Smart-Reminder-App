package com.smartreminder.core.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6FF6FC),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6363),
    tertiary = Color(0xFF4B607C),
    error = Color(0xFFBA1A1A),
    surface = Color(0xFFFAFDFC),
    background = Color(0xFFFAFDFC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CDADF),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF6FF6FC),
    secondary = Color(0xFFB1CCCB),
    tertiary = Color(0xFFB3C8E8),
    error = Color(0xFFFFB4AB),
    surface = Color(0xFF191C1C),
    background = Color(0xFF191C1C),
)

/**
 * Call and in-call screens deliberately opt out of dynamic color via [dynamicColor] = false,
 * so the "incoming call" surface looks the same on every device.
 */
@Composable
fun SmartReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SmartReminderTypography,
        content = content,
    )
}
