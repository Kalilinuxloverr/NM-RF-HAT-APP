package com.nmrf.remote.wifi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.SignalBar
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim

private const val WIFI_HELP =
    "Sichtbare 2.4/5-GHz-Netze. Der Graph oben zeigt jeden AP an seinem Kanal: Höhe = Signal (RSSI), " +
        "Breite = Kanalbreite; Überlappungen = Störungen. Liste nach Signal sortiert. " +
        "Netz antippen → Details + RSSI-Wasserfall. SCAN stößt eine neue Messung an (Android drosselt ~4/2 min)."

@Composable
fun WifiAnalyzerScreen(
    vm: WifiAnalyzerViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var selectedBssid by rememberSaveable { mutableStateOf<String?>(null) }

    if (!hasPermission) {
        PermissionInfo("Standort-Berechtigung nötig", onRequestPermission)
        return
    }
    val selected = state.visible.firstOrNull { it.bssid == selectedBssid }
    if (selected != null) {
        WifiDetailScreen(selected, vm.historyFor(selected.bssid), onBack = { selectedBssid = null })
    } else {
        WifiList(state, vm, onSelect = { selectedBssid = it.bssid })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiList(state: UiState, vm: WifiAnalyzerViewModel, onSelect: (AccessPoint) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "WLAN-ANALYZER",
            "${state.visible.size} Netze · ${if (state.scanning) "scannt…" else "bereit"}",
            help = WIFI_HELP,
            action = { HeaderChip("SCAN") { vm.rescan() } },
        )
        Column(Modifier.padding(horizontal = 12.dp)) {
            val bands = listOf(Band.GHZ_2_4 to "2.4 GHz", Band.GHZ_5 to "5 GHz")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                bands.forEachIndexed { i, (b, label) ->
                    SegmentedButton(
                        selected = state.selectedBand == b,
                        onClick = { vm.selectBand(b) },
                        shape = SegmentedButtonDefaults.itemShape(i, bands.size),
                    ) { Text(label) }
                }
            }
            ChannelGraph(state.visible, Modifier.fillMaxWidth().height(150.dp).padding(vertical = 6.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.visible) { ap -> ApRow(ap, onClick = { onSelect(ap) }) }
            }
        }
    }
}

@Composable
private fun ApRow(ap: AccessPoint, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(ap.ssid.ifBlank { "<hidden>" }, color = MatrixText, fontWeight = FontWeight.Medium)
            Text(
                "ch ${ap.channel} · ${ap.widthMhz}MHz · ${securityOf(ap.capabilities)}",
                fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MatrixTextDim,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${ap.rssi}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MatrixGreen)
            Spacer(Modifier.height(3.dp))
            SignalBar(ap.rssi)
        }
    }
    HorizontalDivider(color = MatrixGreenDark.copy(alpha = 0.5f))
}

@Composable
private fun ChannelGraph(aps: List<AccessPoint>, modifier: Modifier) {
    Canvas(modifier) {
        if (aps.isEmpty()) return@Canvas
        val minCh = aps.minOf { it.channel }
        val maxCh = aps.maxOf { it.channel }
        val span = (maxCh - minCh).coerceAtLeast(1).toFloat()
        drawLine(MatrixGreen.copy(alpha = 0.2f), Offset(0f, size.height), Offset(size.width, size.height), 2f)
        aps.forEach { ap ->
            val norm = ((ap.rssi + 95f) / 65f).coerceIn(0.06f, 1f)
            val cx = ((ap.channel - minCh) / span) * size.width
            val half = (size.width / span) * (ap.widthMhz / 20f) * 0.5f + 14f
            val top = size.height * (1f - norm)
            val fill = Path().apply {
                moveTo(cx - half, size.height); lineTo(cx, top); lineTo(cx + half, size.height); close()
            }
            drawPath(fill, MatrixGreen.copy(alpha = 0.18f))
            drawLine(MatrixGreen, Offset(cx - half, size.height), Offset(cx, top), 2.5f)
            drawLine(MatrixGreen, Offset(cx, top), Offset(cx + half, size.height), 2.5f)
        }
    }
}

@Composable
internal fun PermissionInfo(title: String, onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = MatrixGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Für den Scan braucht die App die Berechtigung; Standort/Funk muss eingeschaltet sein.",
            style = MaterialTheme.typography.bodySmall, color = MatrixText,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Berechtigung erteilen") }
    }
}
