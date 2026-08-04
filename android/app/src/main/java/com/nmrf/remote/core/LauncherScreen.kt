package com.nmrf.remote.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.components.ScopeLine
import com.nmrf.remote.ui.components.StatusPill
import com.nmrf.remote.ui.theme.Elev2
import com.nmrf.remote.ui.theme.LineSoft
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.ui.theme.StatusActive
import com.nmrf.remote.ui.theme.StatusAlert
import com.nmrf.remote.ui.theme.StatusData
import kotlin.math.sin

private data class Tile(val label: String, val glyph: String, val desc: String, val accent: Color, val screen: Screen)

private val TILES = listOf(
    Tile("WLAN", "📶", "Analyzer & Detektor", StatusData, Screen.WIFI),
    Tile("BLE", "🔵", "Scanner & GATT", MatrixGreen, Screen.BLE),
    Tile("SENTINEL", "🛰", "Detektoren · Anti-Stalking", StatusAlert, Screen.DETECT),
    Tile("AUDIO", "🎚", "Live-Spektrogramm", StatusActive, Screen.AUDIO),
    Tile("HAT", "📡", "ESP32-Bridge", StatusData, Screen.HAT),
    Tile("TOOLS", "🧰", "Beacon · OUI · Export", MatrixGreen, Screen.TOOLS),
    Tile("SETTINGS", "⚙", "Transport · Info", MatrixTextDim, Screen.SETTINGS),
)

@Composable
fun LauncherScreen(onOpen: (Screen) -> Unit) {
    // Ruhiges Instrument-Scope: deterministische Phosphor-Spur (Signature, statisch = ehrlich).
    val wave = remember {
        List(72) { i -> (sin(i * 0.28) * 0.6 + sin(i * 0.09) * 0.3 + ((i * 37) % 11 - 5) / 22.0).toFloat() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◉", color = MatrixGreen, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text("NMRF", color = MatrixGreen, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(10.dp))
            Text("SENTINEL", color = MatrixTextDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            StatusPill("● BEREIT", MatrixGreen)
        }

        // Hero: Signature-Scope in gerahmtem Panel
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(MatrixPanel)
                .border(1.dp, LineSoft, RoundedCornerShape(10.dp)),
        ) {
            Column {
                ScopeLine(wave, Modifier.fillMaxWidth().height(78.dp).padding(12.dp))
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                    Text("RF-LAB · passiv", color = MatrixTextDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("2.4 · 5 · 6 GHz + BLE", color = MatrixTextDim, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(TILES) { t -> TileCard(t, onOpen) }
        }
    }
}

@Composable
private fun TileCard(t: Tile, onOpen: (Screen) -> Unit) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(1.25f)
            .clip(RoundedCornerShape(12.dp))
            .background(Elev2)
            .border(1.dp, t.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onOpen(t.screen) }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t.glyph, fontSize = 30.sp, modifier = Modifier.weight(1f))
            Box(Modifier.width(7.dp).height(7.dp).clip(RoundedCornerShape(50)).background(t.accent))
        }
        Column {
            Text(t.label, color = MatrixText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(t.desc, color = MatrixTextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}
