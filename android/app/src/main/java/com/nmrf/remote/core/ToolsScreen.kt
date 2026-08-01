package com.nmrf.remote.core

import android.Manifest
import android.os.Build
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
import com.nmrf.remote.tools.BeaconScreen
import com.nmrf.remote.tools.ExportScreen
import com.nmrf.remote.tools.GattConsoleScreen
import com.nmrf.remote.tools.OuiDbScreen
import com.nmrf.remote.tools.WifiDetectorScreen
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixText

private data class Tool(val id: String, val label: String, val glyph: String)

private val TOOLS = listOf(
    Tool("beacon", "BLE-Beacon", "📡"),
    Tool("wifi", "WLAN-Detektor", "🛡"),
    Tool("gatt", "GATT-Konsole", "🧬"),
    Tool("oui", "OUI/Company", "🔎"),
    Tool("export", "Scans/Export", "💾"),
)

private fun blePerms() = if (Build.VERSION.SDK_INT >= 31)
    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun ToolsScreen(onBack: () -> Unit) {
    var sel by remember { mutableStateOf<String?>(null) }
    val back = { sel = null }
    when (sel) {
        "oui" -> OuiDbScreen(back)
        "export" -> ExportScreen(back)
        "wifi" -> { val p = rememberPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION)); WifiDetectorScreen(back, p.allGranted, p.request) }
        "beacon" -> { val p = rememberPermissions(remember { blePerms() }); BeaconScreen(back, p.allGranted, p.request) }
        "gatt" -> { val p = rememberPermissions(remember { blePerms() }); GattConsoleScreen(back, p.allGranted, p.request) }
        else -> ToolGrid(onBack) { sel = it }
    }
}

@Composable
private fun ToolGrid(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("TOOLS", "Standalone — ohne HAT", action = { HeaderChip("‹ HOME", onBack) })
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(TOOLS) { t ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MatrixPanel)
                        .border(1.dp, MatrixGreenDark, RoundedCornerShape(10.dp)).clickable { onOpen(t.id) }.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.glyph, fontSize = 34.sp)
                    Text(t.label, color = MatrixText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
