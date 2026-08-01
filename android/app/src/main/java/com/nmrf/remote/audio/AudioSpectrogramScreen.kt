package com.nmrf.remote.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.heat
import com.nmrf.remote.ui.components.VerticalWaterfall
import com.nmrf.remote.wifi.PermissionInfo

private const val AUDIO_HELP =
    "Live-FFT des Mikrofons. Spektrogramm: unten tiefe, oben hohe Frequenzen; rechts = jetzt, " +
        "nach links wandernd; Helligkeit = Pegel. Unten die aktuellen Frequenz-Balken. " +
        "STOP/START pausiert die Aufnahme. (Pfeif oder klatsch rein — die Muster wandern live.)"

@Composable
fun AudioSpectrogramScreen(
    vm: AudioSpectrogramViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("AUDIO-SPEKTROGRAMM", "Mikrofon · FFT · 44.1 kHz", help = AUDIO_HELP)
            PermissionInfo("Mikrofon-Berechtigung nötig", onRequestPermission)
        }
        return
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "AUDIO-SPEKTROGRAMM",
            "FFT 2048 · 44.1 kHz · ${if (state.running) "läuft" else "pause"}",
            help = AUDIO_HELP,
            action = { HeaderChip(if (state.running) "STOP" else "START") { vm.setEnabled(!state.running) } },
        )
        VerticalWaterfall(
            state.spectrogram,
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
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
