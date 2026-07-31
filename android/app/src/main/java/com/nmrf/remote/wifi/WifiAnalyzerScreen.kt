package com.nmrf.remote.wifi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.theme.NeonCyan
import com.nmrf.remote.ui.theme.NeonGreen
import com.nmrf.remote.ui.theme.NeonMagenta
import com.nmrf.remote.ui.theme.NmrfTheme

@Composable
fun WifiAnalyzerScreen(
    vm: WifiAnalyzerViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val state by vm.state.collectAsState()
    WifiAnalyzerContent(
        state = state,
        hasPermission = hasPermission,
        onSelectBand = vm::selectBand,
        onRescan = vm::rescan,
        onRequestPermission = onRequestPermission,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiAnalyzerContent(
    state: UiState,
    hasPermission: Boolean,
    onSelectBand: (Band) -> Unit,
    onRescan: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WLAN-Analyzer") },
                actions = {
                    TextButton(onClick = onRescan, enabled = hasPermission) {
                        Text(if (state.scanning) "scannt…" else "Rescan")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            if (!hasPermission) {
                PermissionCard(onRequestPermission)
                return@Column
            }

            val bands = listOf(Band.GHZ_2_4 to "2.4 GHz", Band.GHZ_5 to "5 GHz")
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                bands.forEachIndexed { i, (b, label) ->
                    SegmentedButton(
                        selected = state.selectedBand == b,
                        onClick = { onSelectBand(b) },
                        shape = SegmentedButtonDefaults.itemShape(i, bands.size),
                    ) { Text(label) }
                }
            }

            ChannelGraph(
                aps = state.visible,
                band = state.selectedBand,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(vertical = 8.dp),
            )

            Text("${state.visible.size} Netze", fontWeight = FontWeight.SemiBold)

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.visible) { ap -> ApRow(ap) }
            }
        }
    }
}

@Composable
private fun ChannelGraph(aps: List<AccessPoint>, band: Band, modifier: Modifier) {
    val range = channelRange(band)
    val color = bandColor(band)
    val baseline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Canvas(modifier) {
        val span = (range.last - range.first).coerceAtLeast(1).toFloat()
        fun xOf(ch: Int) = ((ch - range.first) / span) * size.width
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        aps.forEach { ap ->
            val norm = ((ap.rssi + 90f) / 60f).coerceIn(0.05f, 1f)
            val cx = xOf(ap.channel).coerceIn(0f, size.width)
            val half = (size.width / span) * (ap.widthMhz / 20f) * 0.5f + 12f
            val top = size.height * (1f - norm)
            val fill = Path().apply {
                moveTo(cx - half, size.height)
                lineTo(cx, top)
                lineTo(cx + half, size.height)
                close()
            }
            drawPath(fill, color.copy(alpha = 0.20f))
            drawLine(color, Offset(cx - half, size.height), Offset(cx, top), 3f)
            drawLine(color, Offset(cx, top), Offset(cx + half, size.height), 3f)
        }
    }
}

@Composable
private fun ApRow(ap: AccessPoint) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(ap.ssid.ifBlank { "<hidden>" }, fontWeight = FontWeight.Medium)
            Text(
                "Kanal ${ap.channel} · ${bandLabel(ap.band)} · ${ap.widthMhz} MHz",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${ap.rssi} dBm", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            SignalBar(ap.rssi)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun SignalBar(rssi: Int) {
    val norm = ((rssi + 90f) / 60f).coerceIn(0f, 1f)
    val color = when {
        rssi >= -55 -> NeonGreen
        rssi >= -70 -> NeonCyan
        else -> Color(0xFFFF6B6B)
    }
    Box(
        Modifier
            .width(90.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.2f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(norm)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Standort-Berechtigung nötig", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Android koppelt WLAN-Scans an die Standortberechtigung. " +
                "Der Standort muss außerdem eingeschaltet sein.",
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Berechtigung erteilen") }
    }
}

private fun channelRange(b: Band): IntRange = when (b) {
    Band.GHZ_2_4 -> 1..14
    Band.GHZ_5 -> 36..165
    Band.GHZ_6 -> 1..233
    Band.UNKNOWN -> 1..14
}

private fun bandColor(b: Band): Color = when (b) {
    Band.GHZ_2_4 -> NeonGreen
    Band.GHZ_5 -> NeonCyan
    else -> NeonMagenta
}

private fun bandLabel(b: Band): String = when (b) {
    Band.GHZ_2_4 -> "2.4 GHz"
    Band.GHZ_5 -> "5 GHz"
    Band.GHZ_6 -> "6 GHz"
    Band.UNKNOWN -> "?"
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun PreviewWifiAnalyzer() {
    val demo = listOf(
        AccessPoint("HomeNet", "aa", 2412, -42, 1, Band.GHZ_2_4, 20),
        AccessPoint("Nachbar", "bb", 2437, -66, 6, Band.GHZ_2_4, 40),
        AccessPoint("", "cc", 2462, -80, 11, Band.GHZ_2_4, 20),
    )
    NmrfTheme {
        WifiAnalyzerContent(
            state = UiState(demo.sortedByDescending { it.rssi }, Band.GHZ_2_4, false),
            hasPermission = true,
            onSelectBand = {},
            onRescan = {},
            onRequestPermission = {},
        )
    }
}
