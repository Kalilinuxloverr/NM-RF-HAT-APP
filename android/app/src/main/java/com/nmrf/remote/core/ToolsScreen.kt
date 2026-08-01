package com.nmrf.remote.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim

private data class Tool(val label: String, val glyph: String, val desc: String)

private val TOOLS = listOf(
    Tool("BLE-Beacon", "📡", "iBeacon/Eddystone/Manufacturer dekodieren"),
    Tool("WLAN-Detektor", "🛡", "Kanal-Last + Deauth/Beacon-Anomalien (passiv)"),
    Tool("Scans/Export", "💾", "WLAN/BLE/Spektren sichern (CSV/JSON)"),
    Tool("OUI/Company-DB", "🔎", "Hersteller offline nachschlagen"),
    Tool("GATT-Konsole", "🧬", "Characteristics lesen/schreiben/notify"),
)

@Composable
fun ToolsScreen(onBack: () -> Unit) {
    var sel by remember { mutableStateOf<Tool?>(null) }
    val s = sel
    if (s != null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(s.label.uppercase(), s.desc, action = { HeaderChip("‹ TOOLS") { sel = null } })
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.glyph, fontSize = 48.sp)
                Text("kommt in Phase 3", color = MatrixGreen, style = MaterialTheme.typography.titleMedium)
                Text(s.desc, color = MatrixTextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("TOOLS", "Standalone — ohne HAT", action = { HeaderChip("‹ HOME", onBack) })
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(TOOLS) { t ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MatrixPanel)
                        .border(1.dp, MatrixGreenDark, RoundedCornerShape(10.dp)).clickable { sel = t }.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.glyph, fontSize = 34.sp)
                    Text(t.label, color = MatrixText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
