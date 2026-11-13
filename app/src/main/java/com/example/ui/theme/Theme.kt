package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WatchAmoledColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = WatchBlack,
    primaryContainer = CyanMuted,
    onPrimaryContainer = TextPrimary,
    secondary = CoralHeart,
    onSecondary = WatchBlack,
    tertiary = MintBookmark,
    onTertiary = WatchBlack,
    background = WatchBlack,
    onBackground = TextPrimary,
    surface = WatchSurface,
    onSurface = TextPrimary,
    surfaceVariant = WatchSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = WatchBorder
)

@Composable
fun PulsoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WatchAmoledColorScheme,
        typography = Typography,
        content = content
    )
}
