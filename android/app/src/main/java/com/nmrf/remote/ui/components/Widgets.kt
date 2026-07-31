package com.nmrf.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
        Text("> $title", style = MaterialTheme.typography.titleLarge, color = MatrixGreen)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MatrixTextDim)
        }
    }
    HorizontalDivider(color = MatrixGreenDark)
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
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Signal-Balken (RSSI dBm -> Farbe/Länge). */
@Composable
fun SignalBar(rssi: Int, width: androidx.compose.ui.unit.Dp = 92.dp) {
    val norm = ((rssi + 100f) / 70f).coerceIn(0f, 1f)
    Row {
        androidx.compose.foundation.layout.Box(
            Modifier.width(width).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MatrixGreenDark),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth(norm).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(heat(norm)),
            )
        }
    }
}

/** Heatmap-Farbe 0..1: schwarz -> grün -> weißgrün (für Wasserfall/Spektrogramm). */
fun heat(v: Float): Color {
    val c = v.coerceIn(0f, 1f)
    val base = lerp(Color(0xFF001505), MatrixGreen, (c * 1.35f).coerceIn(0f, 1f))
    return lerp(base, Color.White, ((c - 0.75f) / 0.25f).coerceIn(0f, 1f))
}
