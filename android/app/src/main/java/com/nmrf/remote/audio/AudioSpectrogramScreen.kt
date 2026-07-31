package com.nmrf.remote.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.heat
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.wifi.PermissionInfo

@Composable
fun AudioSpectrogramScreen(
    vm: AudioSpectrogramViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("AUDIO-SPEKTROGRAMM", "Mikrofon · FFT · 44.1 kHz")
            PermissionInfo("Mikrofon-Berechtigung nötig", onRequestPermission)
        }
        return
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("AUDIO-SPEKTROGRAMM", "Mikrofon · FFT 2048 · 44.1 kHz")
        Spectrogram(
            state.spectrogram,
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Text(
            "unten = tiefe Frequenzen · rechts = jetzt · Helligkeit = Pegel",
            style = MaterialTheme.typography.bodySmall, color = MatrixTextDim,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        SpectrumBars(state.spectrum, Modifier.fillMaxWidth().height(88.dp).padding(8.dp))
    }
}

@Composable
private fun Spectrogram(cols: List<FloatArray>, modifier: Modifier) {
    Canvas(modifier) {
        if (cols.isEmpty()) return@Canvas
        val colW = size.width / cols.size
        cols.forEachIndexed { x, col ->
            if (col.isEmpty()) return@forEachIndexed
            val cellH = size.height / col.size
            col.forEachIndexed { b, v ->
                val y = size.height - (b + 1) * cellH
                drawRect(heat(v), topLeft = Offset(x * colW, y), size = Size(colW + 1f, cellH + 1f))
            }
        }
    }
}

@Composable
private fun SpectrumBars(col: FloatArray, modifier: Modifier) {
    Canvas(modifier) {
        if (col.isEmpty()) return@Canvas
        val bw = size.width / col.size
        col.forEachIndexed { i, v ->
            val h = size.height * v
            drawRect(heat(v), topLeft = Offset(i * bw, size.height - h), size = Size(bw * 0.85f, h))
        }
    }
}
