package com.maelle.app.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorWhiteSmoke = Color(0xFFF2F7FA)
private val ColorPaleBlue = Color(0xFFD8E6EE)
private val ColorSlate = Color(0xFF334B59)

private val DarkColorScheme = darkColorScheme(
    primary = MaelleAccentStrong,
    onPrimary = MaelleBackground,
    background = MaelleBackground,
    onBackground = MaelleText,
    surface = MaelleSurface,
    onSurface = MaelleText,
    surfaceVariant = MaelleSurfaceVariant,
    onSurfaceVariant = MaelleTextMuted,
)

private val LightColorScheme = lightColorScheme(
    primary = MaelleAccent,
    onPrimary = MaelleBackground,
    background = MaelleText,
    onBackground = MaelleBackground,
    surface = ColorWhiteSmoke,
    onSurface = MaelleBackground,
    surfaceVariant = ColorPaleBlue,
    onSurfaceVariant = ColorSlate,
)

@Composable
fun MaelleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
