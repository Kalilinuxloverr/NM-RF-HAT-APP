package com.nmrf.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NmrfColors = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = MatrixBlack,
    secondary = MatrixGreenDim,
    onSecondary = MatrixBlack,
    tertiary = NeonCyan,
    background = MatrixBlack,
    onBackground = MatrixText,
    surface = MatrixPanel,
    onSurface = MatrixText,
    surfaceVariant = MatrixPanel,
    onSurfaceVariant = MatrixTextDim,
    outline = MatrixGreenDark,
    error = MatrixRed,
)

@Composable
fun NmrfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NmrfColors, typography = NmrfTypography, content = content)
}
