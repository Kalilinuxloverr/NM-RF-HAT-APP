package com.nmrf.remote.core

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Fast-schwarzer Hintergrund mit leichtem grünem Verlauf. */
fun Modifier.appBackground(): Modifier = this.background(
    Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF03110A), Color(0xFF000000))),
)
