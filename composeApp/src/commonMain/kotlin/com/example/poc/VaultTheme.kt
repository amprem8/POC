package com.example.poc

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF030213),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFF2F2F6),
    onSecondary = Color(0xFF030213),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFE9EBEF),
    onSurfaceVariant = Color(0xFF717182),
    outline = Color(0x1A000000),
    outlineVariant = Color(0xFFE5E7EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF8FAFC),
    onPrimary = Color(0xFF111827),
    secondary = Color(0xFF1F2937),
    onSecondary = Color(0xFFF8FAFC),
    background = Color(0xFF171717),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF171717),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF3F3F46),
)

@Composable
fun VaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}



