package com.nmrf.remote.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.abs

private fun hash(i: Int): Int {
    var h = i * 374761393
    h = (h xor (h ushr 13)) * 1274126177
    return h xor (h ushr 16)
}

/** Klassischer „digital rain" als Canvas (nativeCanvas.drawText, effizient). */
@Composable
fun MatrixRain(modifier: Modifier = Modifier, cell: Float = 30f, headAlpha: Int = 235) {
    val glyphs = remember { "0123456789ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾊﾋﾌﾍﾎ<>*#/=".toCharArray() }
    val head = remember {
        android.graphics.Paint().apply {
            color = 0xFFDFFFE6.toInt(); textSize = 26f
            typeface = android.graphics.Typeface.MONOSPACE; isAntiAlias = true
        }
    }
    val body = remember {
        android.graphics.Paint().apply {
            color = 0xFF00FF41.toInt(); textSize = 26f
            typeface = android.graphics.Typeface.MONOSPACE; isAntiAlias = true
        }
    }
    val t = rememberInfiniteTransition(label = "rain")
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "p",
    )
    Canvas(modifier) {
        val cols = (size.width / cell).toInt().coerceAtLeast(1)
        val rows = (size.height / cell).toInt().coerceAtLeast(1)
        val tail = 14
        val step = (phase * 4).toInt()
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            for (col in 0 until cols) {
                val speed = 0.6f + (abs(hash(col)) % 100) / 100f * 1.6f
                val offset = (abs(hash(col * 7 + 3)) % 100) / 100f
                val headRow = (((phase * speed + offset) % 1f) * (rows + tail)).toInt()
                val x = col * cell + 3f
                for (k in 0 until tail) {
                    val row = headRow - k
                    if (row < 0 || row > rows) continue
                    val ch = glyphs[abs(hash(col * 131 + row * 17 + step)) % glyphs.size]
                    val y = row * cell + cell
                    if (k == 0) {
                        head.alpha = headAlpha
                        nc.drawText(ch.toString(), x, y, head)
                    } else {
                        body.alpha = (190 * (1f - k / tail.toFloat())).toInt().coerceIn(12, 190)
                        nc.drawText(ch.toString(), x, y, body)
                    }
                }
            }
        }
    }
}
