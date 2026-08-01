package com.nmrf.remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    help: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("> $title", style = MaterialTheme.typography.titleLarge, color = MatrixGreen)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MatrixTextDim) }
        }
        action?.let { Spacer(Modifier.width(8.dp)); it() }
        help?.let { Spacer(Modifier.width(8.dp)); HelpButton(it) }
    }
    HorizontalDivider(color = MatrixGreenDark)
}

@Composable
fun HeaderChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MatrixGreenDark, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = MatrixGreen, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun HelpButton(text: String) {
    var open by remember { mutableStateOf(false) }
    HeaderChip("?") { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("OK", color = MatrixGreen) } },
            title = { Text("INFO", color = MatrixGreen) },
            text = { Text(text, color = MatrixText, style = MaterialTheme.typography.bodyMedium) },
            containerColor = MatrixPanel,
        )
    }
}

@Composable
fun MatrixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(6.dp))
        .background(MatrixPanel)
        .border(1.dp, MatrixGreenDark, RoundedCornerShape(6.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(12.dp), content = content)
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, color = MatrixTextDim)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MatrixText)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MatrixGreen,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
fun SignalBar(rssi: Int, barWidth: Dp = 92.dp) {
    val norm = ((rssi + 100f) / 70f).coerceIn(0f, 1f)
    Box(
        Modifier.width(barWidth).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MatrixGreenDark),
    ) {
        Box(Modifier.fillMaxWidth(norm).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(heat(norm)))
    }
}

/** Gerahmter RSSI-Wasserfall mit Heat-Spalten, Signal-Trace und dBm-Gitter. */
@Composable
fun WaterfallStrip(history: List<Int>, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, MatrixGreenDark, RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            fun ny(r: Int) = size.height * (1f - ((r + 100f) / 70f).coerceIn(0f, 1f))
            if (history.isNotEmpty()) {
                val colW = size.width / history.size
                history.forEachIndexed { i, r ->
                    val n = ((r + 100f) / 70f).coerceIn(0f, 1f)
                    drawRect(heat(n), topLeft = Offset(i * colW, 0f), size = Size(colW + 1f, size.height))
                }
                val path = Path()
                history.forEachIndexed { i, r ->
                    val x = i * colW + colW / 2f
                    if (i == 0) path.moveTo(x, ny(r)) else path.lineTo(x, ny(r))
                }
                drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(1.5f))
            }
            listOf(-90, -70, -50, -30).forEach { db ->
                val y = ny(db)
                drawLine(MatrixGreenDark.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 1f)
            }
        }
    }
}

/** Heatmap-Farbe 0..1: dunkelgrün -> grün -> weißgrün. */
fun heat(v: Float): Color {
    val c = v.coerceIn(0f, 1f)
    val base = lerp(Color(0xFF001505), MatrixGreen, (c * 1.35f).coerceIn(0f, 1f))
    return lerp(base, Color.White, ((c - 0.75f) / 0.25f).coerceIn(0f, 1f))
}

/** Vertikaler Wasserfall: jede Zeile = ein Spektrum (X=Bin), neueste oben, fließt nach unten. */
@Composable
fun VerticalWaterfall(frames: List<FloatArray>, modifier: Modifier) {
    Canvas(modifier) {
        if (frames.isEmpty()) return@Canvas
        val rowH = size.height / frames.size
        frames.forEachIndexed { i, row ->
            if (row.isEmpty()) return@forEachIndexed
            val y = size.height - (i + 1) * rowH   // ältestes unten, neuestes oben
            val cellW = size.width / row.size
            row.forEachIndexed { b, v ->
                drawRect(heat(v), topLeft = Offset(b * cellW, y), size = Size(cellW + 1f, rowH + 1f))
            }
        }
    }
}
