package com.nmrf.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NmrfColors = darkColorScheme(
    primary = NeonGreen,
    secondary = NeonCyan,
    tertiary = NeonMagenta,
    background = DarkBg,
    surface = DarkSurface,
    onPrimary = DarkBg,
    onBackground = OnDark,
    onSurface = OnDark,
)

@Composable
fun NmrfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NmrfColors, typography = NmrfTypography, content = content)
}
