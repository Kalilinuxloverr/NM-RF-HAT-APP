package com.nmrf.remote.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.components.MatrixRain
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import kotlinx.coroutines.delay

@Composable
fun BootScreen(onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(700))
        delay(1100)
        onDone()
    }
    val t = rememberInfiniteTransition(label = "boot")
    val ping by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "ping",
    )
    val pulse by t.animateFloat(
        0.85f, 1.2f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
    )

    Box(Modifier.fillMaxSize().appBackground(), contentAlignment = Alignment.Center) {
        MatrixRain(Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier.size(150.dp).graphicsLayer {
                    alpha = appear.value
                    val s = 0.75f + 0.25f * appear.value
                    scaleX = s; scaleY = s
                },
            ) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                drawCircle(MatrixGreen.copy(alpha = (1f - ping) * 0.5f), maxR * (0.3f + 0.7f * ping), c, style = Stroke(3f))
                drawCircle(MatrixGreen.copy(alpha = 0.4f), maxR * 0.62f, c, style = Stroke(3f))
                drawCircle(MatrixGreen.copy(alpha = 0.8f), maxR * 0.38f, c, style = Stroke(3.5f))
                drawCircle(MatrixGreen, maxR * 0.12f * pulse, c)
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "N M R F   R E M O T E",
                style = MaterialTheme.typography.headlineSmall,
                color = MatrixText,
                modifier = Modifier.graphicsLayer { alpha = appear.value },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "RF · WLAN · BLE — LAB",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp),
                color = MatrixTextDim,
                modifier = Modifier.graphicsLayer { alpha = appear.value },
            )
        }
    }
}
