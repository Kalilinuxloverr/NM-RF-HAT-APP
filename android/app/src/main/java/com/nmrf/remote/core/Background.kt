package com.nmrf.remote.core

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Dunkler Vertikal-Gradient als App-Hintergrund. */
fun Modifier.appBackground(): Modifier = this.background(
    Brush.verticalGradient(
        listOf(Color(0xFF0B0F14), Color(0xFF10171F), Color(0xFF0B0F14)),
    ),
)
