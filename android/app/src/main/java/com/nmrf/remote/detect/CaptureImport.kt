package com.nmrf.remote.detect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.components.AccentCard
import com.nmrf.remote.ui.components.EmptyState
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.MatrixCard
import com.nmrf.remote.ui.components.MetricTile
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.SectionLabel
import com.nmrf.remote.ui.components.StatusPill
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.ui.theme.StatusActive
import com.nmrf.remote.ui.theme.StatusAlert
import com.nmrf.remote.ui.theme.StatusData
import com.nmrf.remote.ui.theme.StatusOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Modelle + reine Parser (testbar)
// ---------------------------------------------------------------------------

data class ImpAp(val bssid: String, val channel: Int, val privacy: String, val power: Int, val beacons: Int, val essid: String)
data class ImpStation(val mac: String, val power: Int, val packets: Int, val bssid: String, val probes: String?)
data class Imported(val kind: String, val aps: List<ImpAp>, val stations: List<ImpStation>, val note: String)

/** airodump-ng CSV: AP-Sektion + Station-Sektion. */
fun parseAirodumpCsv(text: String): Imported {
    val aps = ArrayList<ImpAp>(); val stations = ArrayList<ImpStation>()
    var section = 0
    for (raw in text.split("\n")) {
        val line = raw.trim()
        when {
            line.isEmpty() -> section = 0
            line.startsWith("BSSID,") -> section = 1
            line.startsWith("Station MAC,") -> section = 2
            section == 1 -> {
                val f = line.split(",").map { it.trim() }
                if (f.size >= 14) aps.add(ImpAp(f[0], f[3].toIntOrNull() ?: 0, f[5], f[8].toIntOrNull() ?: 0, f[9].toIntOrNull() ?: 0, f[13]))
            }
            section == 2 -> {
                val f = line.split(",").map { it.trim() }
                if (f.size >= 6) stations.add(ImpStation(f[0], f[3].toIntOrNull() ?: 0, f[4].toIntOrNull() ?: 0, f[5], f.drop(6).joinToString(",").trim().ifBlank { null }))
            }
        }
    }
    return Imported("airodump", aps, stations, "airodump-ng CSV")
}

/** WiGLE-CSV (WigleWifi-1.x) → als APs. */
fun parseWigleCsv(text: String): Imported {
    val aps = ArrayList<ImpAp>()
    text.split("\n").forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("WigleWifi") || line.startsWith("MAC,")) return@forEach
        val f = line.split(",").map { it.trim().trim('"') }
        if (f.size >= 6) aps.add(ImpAp(f[0], f[4].toIntOrNull() ?: 0, f[2], f[5].toIntOrNull() ?: 0, 0, f[1]))
    }
    return Imported("wigle", aps, emptyList(), "WiGLE CSV")
}

/** pcap-Header prüfen + Pakete zählen (kein Frame-Decode). */
fun parsePcapSummary(bytes: ByteArray): Imported? {
    if (bytes.size < 24) return null
    fun u32(o: Int, le: Boolean): Long {
        val b = (0..3).map { bytes[o + it].toLong() and 0xFF }
        return if (le) b[0] or (b[1] shl 8) or (b[2] shl 16) or (b[3] shl 24)
        else (b[0] shl 24) or (b[1] shl 16) or (b[2] shl 8) or b[3]
    }
    val magic = u32(0, true)
    val le = when (magic) { 0xA1B2C3D4L -> true; 0xD4C3B2A1L -> false; else -> return null }
    val linktype = u32(20, le).toInt()
    var off = 24; var pkts = 0
    while (off + 16 <= bytes.size) {
        val inclLen = u32(off + 8, le).toInt()
        if (inclLen < 0 || off + 16 + inclLen > bytes.size) break
        pkts++; off += 16 + inclLen
    }
    val lt = when (linktype) { 105 -> "802.11"; 127 -> "radiotap"; 1 -> "ethernet"; else -> "type $linktype" }
    return Imported("pcap", emptyList(), emptyList(), "pcap · $lt · $pkts Pakete")
}

private fun classify(bytes: ByteArray): Imported? {
    parsePcapSummary(bytes)?.let { return it }
    val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return null
    return when {
        text.startsWith("WigleWifi") -> parseWigleCsv(text)
        text.startsWith("BSSID,") || text.contains("Station MAC,") -> parseAirodumpCsv(text)
        else -> null
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun CaptureImportScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Imported?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()?.let { classify(it) }
            }
            if (parsed == null) { error = "Format nicht erkannt (airodump-CSV / WiGLE / pcap)"; result = null } else { error = null; result = parsed }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("CAPTURE-IMPORT", "extern aufgenommen → hier zeigen", action = { HeaderChip("‹ SENTINEL", onBack) })
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderChip("📂 Datei wählen") { picker.launch(arrayOf("*/*")) }
            result?.let { HeaderChip("✕ leeren") { result = null } }
        }
        error?.let { Text(it, color = StatusAlert, modifier = Modifier.padding(horizontal = 12.dp)) }

        val r = result
        if (r == null) {
            EmptyState("📥", "Capture importieren", "Mit externem Adapter (TP-Link, Monitor-Mode) via airodump-ng aufnehmen — dann hier die .csv oder .pcap wählen. Zeigt Netze, Clients & Probes im SENTINEL-Look.")
        } else {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Netze", "${r.aps.size}", accent = StatusData, modifier = Modifier.weight(1f))
                MetricTile("Clients", "${r.stations.size}", accent = StatusActive, modifier = Modifier.weight(1f))
            }
            Text(r.note, color = MatrixTextDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.aps.isNotEmpty()) item { SectionLabel("NETZE") }
                items(r.aps) { ap ->
                    AccentCard(if (isOpenAuth(ap.privacy)) StatusAlert else StatusOk) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ap.essid.ifBlank { "<hidden>" }, color = MatrixText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StatusPill("CH ${ap.channel}", StatusData)
                        }
                        Text("${ap.bssid} · ${ap.privacy.ifBlank { "OPEN" }} · ${ap.power} dBm", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                if (r.stations.isNotEmpty()) item { SectionLabel("CLIENTS") }
                items(r.stations) { st ->
                    MatrixCard {
                        Text(st.mac, color = MatrixText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("→ ${st.bssid} · ${st.packets} Pkt · ${st.power} dBm", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        st.probes?.let { Text("Probes: $it", color = StatusActive, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

private fun isOpenAuth(privacy: String): Boolean {
    val p = privacy.uppercase()
    return p.isBlank() || p == "OPN" || p == "OPEN" || (!p.contains("WPA") && !p.contains("WEP") && !p.contains("RSN"))
}
