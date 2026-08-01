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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim

private data class Tile(val label: String, val glyph: String, val screen: Screen)

private val TILES = listOf(
    Tile("WLAN", "📶", Screen.WIFI),
    Tile("BLE", "🔵", Screen.BLE),
    Tile("AUDIO", "🎚", Screen.AUDIO),
    Tile("HAT", "🛰", Screen.HAT),
    Tile("TOOLS", "🧰", Screen.TOOLS),
    Tile("SETTINGS", "⚙", Screen.SETTINGS),
)

@Composable
fun LauncherScreen(onOpen: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◉", color = MatrixGreen, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "NMRF REMOTE",
                color = MatrixGreen,
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 3.sp),
            )
            Spacer(Modifier.width(8.dp))
            Text("LAB", color = MatrixTextDim, style = MaterialTheme.typography.labelMedium)
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
        Modifier.fillMaxWidth().aspectRatio(1.15f)
            .clip(RoundedCornerShape(10.dp))
            .background(MatrixPanel)
            .border(1.dp, MatrixGreenDark, RoundedCornerShape(10.dp))
            .clickable { onOpen(t.screen) }
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t.glyph, fontSize = 40.sp)
        Spacer(Modifier.width(8.dp))
        Text(t.label, color = MatrixText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}
