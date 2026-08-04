package com.nmrf.remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.theme.Elev2
import com.nmrf.remote.ui.theme.LineSoft
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.ui.theme.StatusActive
import com.nmrf.remote.ui.theme.StatusAlert
import com.nmrf.remote.ui.theme.StatusOk

/** Last-/Bedrohungs-Heat 0..1: grün → amber → rot (Bedeutung: mehr = schlechter). */
fun heatDanger(v: Float): Color {
    val c = v.coerceIn(0f, 1f)
    return if (c < 0.5f) lerp(StatusOk, StatusActive, c / 0.5f)
    else lerp(StatusActive, StatusAlert, (c - 0.5f) / 0.5f)
}

/** Status-Chip mit Punkt: Farbe kodiert den Zustand, nicht die Deko. */
@Composable
fun StatusPill(text: String, color: Color = MatrixGreen, filled: Boolean = false) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** Großer Messwert mit Einheit und Label — die Bausteine der Telemetrie-Leiste. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String? = null,
    accent: Color = MatrixGreen,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MatrixPanel)
            .border(1.dp, LineSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), color = MatrixTextDim, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = accent, style = MaterialTheme.typography.headlineMedium)
            unit?.let {
                Spacer(Modifier.width(4.dp))
                Text(
                    it, color = MatrixTextDim,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

/** Karte mit farbiger Akzent-Schiene links (Schweregrad) + leichter Tönung. */
@Composable
fun AccentCard(
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Elev2)
        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.weight(1f).padding(12.dp), content = content)
    }
}

/** Leerer Screen ist eine Einladung, nicht Stimmung: sag was zu tun ist. */
@Composable
fun EmptyState(glyph: String, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = MatrixText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(hint, color = MatrixTextDim, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

/**
 * Signature: Phosphor-Oszilloskop-Spur. Werte auf min/max normalisiert,
 * gezeichnet als leichte Fläche + glühender Linienzug über einem Mittelraster.
 */
@Composable
fun ScopeLine(values: List<Float>, modifier: Modifier, color: Color = MatrixGreen) {
    Canvas(modifier) {
        drawLine(color.copy(alpha = 0.12f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
        if (values.size < 2) return@Canvas
        val lo = values.minOrNull() ?: return@Canvas
        val hi = values.maxOrNull() ?: return@Canvas
        val span = (hi - lo).let { if (it < 1e-3f) 1f else it }
        val pad = size.height * 0.14f
        val usable = size.height - pad * 2
        val dx = size.width / (values.size - 1)
        fun y(v: Float) = pad + usable * (1f - (v - lo) / span)
        val line = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = i * dx
            val yy = y(v)
            if (i == 0) { line.moveTo(x, yy); area.moveTo(x, size.height); area.lineTo(x, yy) } else { line.lineTo(x, yy); area.lineTo(x, yy) }
        }
        area.lineTo(size.width, size.height)
        area.close()
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(line, color.copy(alpha = 0.25f), style = Stroke(5f, cap = StrokeCap.Round)) // Glow
        drawPath(line, color, style = Stroke(1.8f, cap = StrokeCap.Round))
    }
}

/** Kleiner Inline-Trend (z.B. RSSI-Verlauf in einer Zeile). */
@Composable
fun Sparkline(values: List<Int>, modifier: Modifier, color: Color = MatrixGreen) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.minOrNull() ?: return@Canvas
        val hi = values.maxOrNull() ?: return@Canvas
        val span = (hi - lo).let { if (it == 0) 1 else it }
        val dx = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * dx
            val yy = size.height * (1f - (v - lo).toFloat() / span)
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        drawPath(path, color, style = Stroke(1.5f, cap = StrokeCap.Round))
    }
}

/** Radial-Gauge (270°-Bogen) für Peil-/Näherungsanzeige. fraction 0..1. */
@Composable
fun RadialGauge(fraction: Float, big: String, sub: String, color: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.09f
            val inset = stroke / 2f + 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                MatrixGreenDark, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color, startAngle = 135f, sweepAngle = 270f * fraction.coerceIn(0f, 1f), useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(sub, color = MatrixTextDim, style = MaterialTheme.typography.labelMedium)
        }
    }
}
