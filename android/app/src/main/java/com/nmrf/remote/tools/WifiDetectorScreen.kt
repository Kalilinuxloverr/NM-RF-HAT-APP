package com.nmrf.remote.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.heat
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.wifi.Band
import com.nmrf.remote.wifi.WifiScanner
import kotlinx.coroutines.flow.map

@Composable
fun WifiDetectorScreen(onBack: () -> Unit, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    val ctx = LocalContext.current
    val scanner = remember { WifiScanner(ctx.applicationContext) }
    val aps by remember { scanner.results }.collectAsState(initial = emptyList())

    // Kanal-Last (2.4 GHz 1-13)
    val load = remember(aps) {
        val m = IntArray(14)
        aps.filter { it.band == Band.GHZ_2_4 && it.channel in 1..13 }.forEach { m[it.channel]++ }
        m
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("WLAN-DETEKTOR", "passiv · Kanal-Auslastung 2.4 GHz", action = { HeaderChip("‹ TOOLS", onBack) })
        if (!hasPermission) {
            Column(Modifier.padding(24.dp)) {
                Text("Standort-Berechtigung nötig (Android koppelt WLAN-Scan daran).", color = MatrixText)
                HeaderChip("Berechtigung", onRequestPermission)
            }
            return
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(12.dp)) {
            val max = (load.maxOrNull() ?: 1).coerceAtLeast(1)
            val bw = size.width / 13f
            for (ch in 1..13) {
                val n = load[ch]
                val h = size.height * (n.toFloat() / max)
                drawRect(heat(n.toFloat() / max), topLeft = Offset((ch - 1) * bw, size.height - h), size = Size(bw * 0.8f, h))
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items((1..13).toList()) { ch ->
                val n = load[ch]
                val strongest = aps.filter { it.band == Band.GHZ_2_4 && it.channel == ch }.maxByOrNull { it.rssi }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("CH $ch", Modifier.width(56.dp), color = MatrixGreen, fontFamily = FontFamily.Monospace)
                    Text("$n APs", Modifier.width(64.dp), color = MatrixText)
                    Text(strongest?.let { "stärkster ${it.rssi} dBm · ${it.ssid.ifBlank { "<hidden>" }}" } ?: "—", color = MatrixTextDim)
                }
            }
        }
    }
}
