package com.nmrf.remote.detect

import android.Manifest
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ble.BleScanner
import com.nmrf.remote.ble.BleSource
import com.nmrf.remote.core.rememberPermissions
import com.nmrf.remote.ui.components.AccentCard
import com.nmrf.remote.ui.components.EmptyState
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.MatrixCard
import com.nmrf.remote.ui.components.MetricTile
import com.nmrf.remote.ui.components.RadialGauge
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.SectionLabel
import com.nmrf.remote.ui.components.Sparkline
import com.nmrf.remote.ui.components.StatusPill
import com.nmrf.remote.ui.components.VerticalWaterfall
import com.nmrf.remote.ui.components.heat
import com.nmrf.remote.ui.components.heatDanger
import com.nmrf.remote.ui.theme.Elev2
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.ui.theme.StatusActive
import com.nmrf.remote.ui.theme.StatusAlert
import com.nmrf.remote.ui.theme.StatusData
import com.nmrf.remote.ui.theme.StatusOk
import com.nmrf.remote.ui.theme.StatusWarn
import com.nmrf.remote.wifi.Band
import com.nmrf.remote.wifi.PermissionInfo
import com.nmrf.remote.wifi.WifiScanner
import com.nmrf.remote.wifi.WifiSource

// ===========================================================================
// Hub
// ===========================================================================

private data class DetectTool(val id: String, val label: String, val glyph: String, val desc: String, val accent: Color)

private val DETECT_TOOLS = listOf(
    DetectTool("rogue", "Rogue-AP", "🛰", "Evil-Twin & Klon-Netze", StatusAlert),
    DetectTool("tracker", "Tracker-Radar", "🎯", "Verfolger / AirTag-Jagd", StatusWarn),
    DetectTool("hunter", "Peilhilfe", "📡", "Gerät orten — heiß/kalt", StatusData),
    DetectTool("spectrum", "Spektrum", "📊", "Kanal-Wasserfall live", StatusActive),
    DetectTool("capture", "Capture", "⏺", "Session + CSV/JSON-Export", StatusOk),
    DetectTool("adinspect", "AD-Inspektor", "🧬", "Advertisement zerlegen", StatusData),
)

private fun blePerms() = if (Build.VERSION.SDK_INT >= 31)
    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

private val FINE = listOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun DetectHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ble = remember { BleScanner(ctx.applicationContext) }
    val wifi = remember { WifiScanner(ctx.applicationContext) }
    var sel by remember { mutableStateOf<String?>(null) }
    val back = { sel = null }
    when (sel) {
        "rogue" -> { val p = rememberPermissions(FINE); RogueApScreen(back, wifi, p.allGranted, p.request) }
        "tracker" -> { val p = rememberPermissions(remember { blePerms() }); TrackerScreen(back, ble, p.allGranted, p.request) }
        "hunter" -> { val p = rememberPermissions(remember { blePerms() }); HunterScreen(back, ble, p.allGranted, p.request) }
        "spectrum" -> { val p = rememberPermissions(FINE); SpectrumScreen(back, wifi, p.allGranted, p.request) }
        "capture" -> { val p = rememberPermissions(remember { blePerms() + FINE }); CaptureScreen(back, ble, wifi, p.allGranted, p.request) }
        "adinspect" -> { val p = rememberPermissions(remember { blePerms() }); AdInspectorScreen(back, ble, p.allGranted, p.request) }
        else -> DetectGrid(onBack) { sel = it }
    }
}

@Composable
private fun DetectGrid(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("SENTINEL", "Detektoren & Signal-Analyse", action = { HeaderChip("‹ HOME", onBack) })
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(DETECT_TOOLS) { t ->
                Column(
                    Modifier.fillMaxWidth().aspectRatio(1.05f)
                        .clip(RoundedCornerShape(10.dp)).background(Elev2)
                        .border(1.dp, t.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable { onOpen(t.id) }.padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(t.glyph, fontSize = 32.sp)
                    Column {
                        Text(t.label, color = MatrixText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(t.desc, color = MatrixTextDim, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun sevColor(s: Severity) = when (s) {
    Severity.ALERT -> StatusAlert
    Severity.WARN -> StatusWarn
    Severity.INFO -> StatusData
}

// ===========================================================================
// Rogue-AP / Evil-Twin
// ===========================================================================

@Composable
private fun RogueApScreen(onBack: () -> Unit, wifi: WifiSource, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("ROGUE-AP", "Evil-Twin-Detektor", action = { HeaderChip("‹ SENTINEL", onBack) })
            PermissionInfo("Standort-Berechtigung nötig (Android koppelt WLAN-Scan daran).", onRequestPermission)
        }
        return
    }
    val aps by remember { wifi.results }.collectAsState(initial = wifi.latest())
    val findings = remember(aps) { RogueApAnalyzer.analyze(aps) }
    val alerts = findings.count { it.severity == Severity.ALERT }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("ROGUE-AP", "${aps.size} Netze · ${findings.size} Auffälligkeiten", action = { HeaderChip("‹ SENTINEL", onBack) })
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Netze", "${aps.size}", accent = MatrixGreen, modifier = Modifier.weight(1f))
            MetricTile("Warnungen", "${findings.size - alerts}", accent = StatusWarn, modifier = Modifier.weight(1f))
            MetricTile("Alarme", "$alerts", accent = if (alerts > 0) StatusAlert else MatrixTextDim, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 12.dp)) { HeaderChip("↻ neu scannen") { wifi.requestScan() } }
        if (findings.isEmpty()) {
            EmptyState("🛡", "Keine Auffälligkeiten", "Kein Klon-Netz und keine offene Kopie in Reichweite. Neu scannen, um erneut zu prüfen.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(findings) { f ->
                    AccentCard(sevColor(f.severity)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.ssid.ifBlank { "<hidden>" }, color = MatrixText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StatusPill(f.severity.name, sevColor(f.severity), filled = true)
                        }
                        Text(f.title, color = sevColor(f.severity), style = MaterialTheme.typography.bodyMedium)
                        Text(f.detail, color = MatrixTextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        f.bssids.take(6).forEach {
                            Text("· $it", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Tracker / Follower-Radar
// ===========================================================================

private class Acc(var firstSeen: Long) {
    var lastSeen = firstSeen
    var count = 0
    var rssiSum = 0
    var rssiN = 0
    var name: String? = null
    var companyId: Int? = null
    var serviceUuids: List<String> = emptyList()
    var connectable = false
    var rssiHistory: List<Int> = emptyList()
}

@Composable
private fun TrackerScreen(onBack: () -> Unit, ble: BleSource, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("TRACKER-RADAR", "Anti-Stalking", action = { HeaderChip("‹ SENTINEL", onBack) })
            PermissionInfo("Bluetooth-Berechtigung nötig.", onRequestPermission)
        }
        return
    }
    val acc = remember { HashMap<String, Acc>() }
    var findings by remember { mutableStateOf<List<TrackerFinding>>(emptyList()) }
    var seen by remember { mutableStateOf(0) }
    var elapsedMin by remember { mutableStateOf(0L) }
    val start = remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        ble.devices.collect { list ->
            val now = System.currentTimeMillis()
            if (start.value == 0L) start.value = now
            list.forEach { d ->
                val a = acc.getOrPut(d.address) { Acc(now) }
                a.lastSeen = now; a.count++
                a.rssiSum += d.rssi; a.rssiN++
                d.name?.let { a.name = it }
                d.companyId?.let { a.companyId = it }
                if (d.serviceUuids.isNotEmpty()) a.serviceUuids = d.serviceUuids
                a.connectable = d.connectable
                a.rssiHistory = d.rssiHistory
            }
            val sightings = acc.map { (addr, a) ->
                TrackSighting(addr, a.name, a.companyId, a.serviceUuids, a.connectable, a.firstSeen, a.lastSeen, a.count, if (a.rssiN > 0) a.rssiSum / a.rssiN else -100, a.rssiHistory)
            }
            findings = TrackerAnalyzer.analyze(sightings, now)
            seen = acc.size
            elapsedMin = (now - start.value) / 60_000
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("TRACKER-RADAR", "läuft seit $elapsedMin min · $seen Geräte gesehen", action = { HeaderChip("‹ SENTINEL", onBack) })
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Verdächtig", "${findings.size}", accent = if (findings.isEmpty()) MatrixTextDim else StatusWarn, modifier = Modifier.weight(1f))
            MetricTile("Beobachtet", "$seen", unit = "dev", accent = MatrixGreen, modifier = Modifier.weight(1f))
            MetricTile("Laufzeit", "$elapsedMin", unit = "min", accent = StatusData, modifier = Modifier.weight(1f))
        }
        if (findings.isEmpty()) {
            EmptyState("🎯", "Radar läuft — lauf ein paar Minuten mit", "Der Radar merkt sich, welche BLE-Geräte dir über Zeit folgen. Bekannte Tags (AirTag/Tile/SmartTag) meldet er sofort, unbekannte nach anhaltender Nähe.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(findings) { f ->
                    val col = when { f.score >= 70 -> StatusAlert; f.score >= 45 -> StatusWarn; else -> StatusData }
                    AccentCard(col) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.sighting.name ?: "<unbekannt>", color = MatrixText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StatusPill("SCORE ${f.score}", col, filled = true)
                        }
                        Text(f.reason, color = col, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.sighting.address, color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("${f.sighting.rssiAvg} dBm", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                        if (f.sighting.rssiHistory.size > 1) {
                            Sparkline(f.sighting.rssiHistory, Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp), col)
                        }
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Peilhilfe / Hunter (BLE — schnelles RSSI)
// ===========================================================================

@Composable
private fun HunterScreen(onBack: () -> Unit, ble: BleSource, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("PEILHILFE", "Gerät orten", action = { HeaderChip("‹ SENTINEL", onBack) })
            PermissionInfo("Bluetooth-Berechtigung nötig.", onRequestPermission)
        }
        return
    }
    val devs by remember { ble.devices }.collectAsState(initial = emptyList())
    var target by remember { mutableStateOf<String?>(null) }
    var smoothed by remember { mutableStateOf(-100f) }
    var prev by remember { mutableStateOf(-100f) }
    val dev = devs.firstOrNull { it.address == target }

    LaunchedEffect(dev?.rssi, target) {
        val r = dev?.rssi ?: return@LaunchedEffect
        prev = smoothed
        smoothed = if (smoothed <= -100f) r.toFloat() else smoothed * 0.7f + r * 0.3f
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("PEILHILFE", target?.let { "Ziel: $it" } ?: "Ziel wählen", action = { HeaderChip("‹ SENTINEL", onBack) })
        if (target == null) {
            Text("Gerät antippen — die Peilhilfe zeigt dann heiß/kalt beim Näherkommen.", color = MatrixTextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            if (devs.isEmpty()) {
                EmptyState("📡", "Kein Gerät in Reichweite", "Sobald BLE-Geräte auftauchen, erscheinen sie hier zur Auswahl.")
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(devs.sortedByDescending { it.rssi }) { d ->
                        Row(Modifier.fillMaxWidth().clickable { target = d.address; smoothed = -100f }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name ?: "<unbekannt>", color = MatrixText, fontWeight = FontWeight.Medium)
                                Text(d.address, color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                            Text("${d.rssi}", color = MatrixGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val frac = ((smoothed + 100f) / 70f).coerceIn(0f, 1f)
            val hotter = smoothed - prev
            val trend = when { hotter > 0.6f -> "🔥 WÄRMER"; hotter < -0.6f -> "❄ KÄLTER"; else -> "— halten" }
            val trendCol = when { hotter > 0.6f -> StatusAlert; hotter < -0.6f -> StatusData; else -> MatrixTextDim }
            val dist = when { smoothed >= -55f -> "sehr nah"; smoothed >= -70f -> "nah"; smoothed >= -85f -> "mittel"; else -> "fern" }
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RadialGauge(frac, "${smoothed.toInt()}", "dBm · $dist", heat(frac), Modifier.fillMaxWidth().height(240.dp))
                Spacer(Modifier.height(12.dp))
                StatusPill(trend, trendCol, filled = true)
                Spacer(Modifier.height(16.dp))
                dev?.let {
                    if (it.rssiHistory.size > 1) Sparkline(it.rssiHistory, Modifier.fillMaxWidth().height(48.dp), heat(frac))
                    Text(it.name ?: it.address, color = MatrixTextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                } ?: Text("Ziel nicht mehr in Reichweite…", color = StatusWarn, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(16.dp))
                HeaderChip("‹ Ziel wechseln") { target = null }
            }
        }
    }
}

// ===========================================================================
// Spektrum — Live Kanal-Wasserfall
// ===========================================================================

private fun bandChannels(band: Band): List<Int> = when (band) {
    Band.GHZ_2_4 -> (1..14).toList()
    Band.GHZ_5 -> listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
    Band.GHZ_6 -> (1..93 step 4).toList()
    Band.UNKNOWN -> emptyList()
}

@Composable
private fun SpectrumScreen(onBack: () -> Unit, wifi: WifiSource, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("SPEKTRUM", "Kanal-Wasserfall", action = { HeaderChip("‹ SENTINEL", onBack) })
            PermissionInfo("Standort-Berechtigung nötig (Android koppelt WLAN-Scan daran).", onRequestPermission)
        }
        return
    }
    var band by remember { mutableStateOf(Band.GHZ_2_4) }
    val aps by remember { wifi.results }.collectAsState(initial = wifi.latest())
    val frames = remember { mutableStateListOf<FloatArray>() }
    val channels = remember(band) { bandChannels(band) }

    LaunchedEffect(aps, band) {
        if (channels.isEmpty()) return@LaunchedEffect
        val load = SpectrumAnalyzer.channelLoad(aps, band).associateBy { it.channel }
        val maxN = (load.values.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
        val row = FloatArray(channels.size) { i -> (load[channels[i]]?.count ?: 0).toFloat() / maxN }
        frames.add(row)
        while (frames.size > 48) frames.removeAt(0)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("SPEKTRUM", "${aps.count { it.band == band }} Netze im Band", action = { HeaderChip("‹ SENTINEL", onBack) })
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Band.GHZ_2_4 to "2.4", Band.GHZ_5 to "5", Band.GHZ_6 to "6").forEach { (b, lbl) ->
                val on = band == b
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (on) StatusActive.copy(alpha = 0.18f) else MatrixPanel)
                        .border(1.dp, if (on) StatusActive else MatrixGreenDark, RoundedCornerShape(6.dp))
                        .clickable { band = b; frames.clear() }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("$lbl GHz", color = if (on) StatusActive else MatrixTextDim, style = MaterialTheme.typography.labelLarge) }
            }
        }
        SectionLabelPad("WASSERFALL (neu oben)")
        VerticalWaterfall(frames.toList(), Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 12.dp))
        SectionLabelPad("KANAL-BELEGUNG")
        val load = remember(aps, band) { SpectrumAnalyzer.channelLoad(aps, band) }
        val maxN = (load.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(load) { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("CH ${c.channel}", Modifier.width(58.dp), color = MatrixGreen, fontFamily = FontFamily.Monospace)
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(MatrixGreenDark)) {
                        Box(Modifier.fillMaxWidth(c.count.toFloat() / maxN).height(10.dp).clip(RoundedCornerShape(3.dp)).background(heatDanger(c.count.toFloat() / maxN)))
                    }
                    Text(" ${c.count}·${c.maxRssi}dBm", Modifier.padding(start = 6.dp), color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionLabelPad(text: String) {
    Box(Modifier.padding(horizontal = 12.dp)) { SectionLabel(text) }
}

// ===========================================================================
// Capture — Session-Recorder + Export
// ===========================================================================

private data class CapEvent(val t: Long, val kind: String, val id: String, val name: String, val rssi: Int)

@Composable
private fun CaptureScreen(onBack: () -> Unit, ble: BleSource, wifi: WifiSource, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("CAPTURE", "Session-Recorder", action = { HeaderChip("‹ SENTINEL", onBack) })
            PermissionInfo("Bluetooth- und Standort-Berechtigung nötig.", onRequestPermission)
        }
        return
    }
    val ctx = LocalContext.current
    val events = remember { mutableStateListOf<CapEvent>() }
    val seenKeys = remember { HashSet<String>() }
    var recording by remember { mutableStateOf(false) }
    val start = remember { mutableStateOf(0L) }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        ble.devices.collect { list ->
            val now = System.currentTimeMillis()
            list.forEach { d ->
                val k = "B:${d.address}"
                if (seenKeys.add(k)) events.add(CapEvent(now, "BLE", d.address, d.name ?: "", d.rssi))
            }
        }
    }
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        wifi.results.collect { list ->
            val now = System.currentTimeMillis()
            list.forEach { ap ->
                val k = "W:${ap.bssid}"
                if (seenKeys.add(k)) events.add(CapEvent(now, "WIFI", ap.bssid, ap.ssid, ap.rssi))
            }
        }
    }

    fun share(name: String, mime: String, body: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime; putExtra(Intent.EXTRA_SUBJECT, name); putExtra(Intent.EXTRA_TEXT, body)
        }
        ctx.startActivity(Intent.createChooser(i, name))
    }

    val wifiN = events.count { it.kind == "WIFI" }
    val bleN = events.count { it.kind == "BLE" }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("CAPTURE", if (recording) "● aufzeichnung läuft" else "bereit", action = { HeaderChip("‹ SENTINEL", onBack) })
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Events", "${events.size}", accent = MatrixGreen, modifier = Modifier.weight(1f))
            MetricTile("WLAN", "$wifiN", accent = StatusData, modifier = Modifier.weight(1f))
            MetricTile("BLE", "$bleN", accent = StatusActive, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!recording) HeaderChip("⏺ START") { recording = true; start.value = System.currentTimeMillis() }
            else HeaderChip("⏹ STOP") { recording = false }
            HeaderChip("⟲ leeren") { events.clear(); seenKeys.clear() }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderChip("CSV teilen") { if (events.isNotEmpty()) share("nmrf-capture.csv", "text/csv", eventsCsv(events)) }
            HeaderChip("JSON teilen") { if (events.isNotEmpty()) share("nmrf-capture.json", "application/json", eventsJson(events)) }
        }
        if (events.isEmpty()) {
            EmptyState("⏺", if (recording) "Sammle Beobachtungen…" else "Noch nichts aufgezeichnet", "START drücken — jedes neu gesehene WLAN/BLE-Gerät landet als Event mit Zeitstempel im Log. Danach als CSV/JSON teilen.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), reverseLayout = true) {
                items(events) { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(e.kind, if (e.kind == "WIFI") StatusData else StatusActive)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name.ifBlank { "<ohne Name>" }, color = MatrixText, style = MaterialTheme.typography.bodyMedium)
                            Text(e.id, color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                        Text("${e.rssi}", color = MatrixGreen, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

private fun eventsCsv(events: List<CapEvent>): String =
    "t_ms,kind,id,name,rssi\n" + events.joinToString("\n") { "${it.t},${it.kind},${it.id},\"${it.name}\",${it.rssi}" }

private fun eventsJson(events: List<CapEvent>): String =
    events.joinToString(",", "[", "]") {
        """{"t":${it.t},"kind":"${it.kind}","id":"${it.id}","name":"${it.name.replace("\"", "'")}","rssi":${it.rssi}}"""
    }

// ===========================================================================
// AD-Inspektor — Advertisement zerlegen
// ===========================================================================

@Composable
private fun AdInspectorScreen(onBack: () -> Unit, ble: BleSource, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("AD-INSPEKTOR", "Advertisement-Parser", action = { HeaderChip("‹ SENTINEL", onBack) })
            PermissionInfo("Bluetooth-Berechtigung nötig.", onRequestPermission)
        }
        return
    }
    val devs by remember { ble.devices }.collectAsState(initial = emptyList())
    var sel by remember { mutableStateOf<String?>(null) }
    val dev = devs.firstOrNull { it.address == sel }

    if (dev == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("AD-INSPEKTOR", "${devs.size} Geräte · Gerät wählen", action = { HeaderChip("‹ SENTINEL", onBack) })
            if (devs.isEmpty()) {
                EmptyState("🧬", "Kein Gerät in Reichweite", "Der Inspektor zerlegt das rohe Advertisement in seine AD-Strukturen (Flags, Namen, UUIDs, Hersteller-TLV).")
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(devs.sortedByDescending { it.rssi }) { d ->
                        Row(Modifier.fillMaxWidth().clickable { sel = d.address }.padding(vertical = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name ?: "<unbekannt>", color = MatrixText, fontWeight = FontWeight.Medium)
                                Text("${d.address} · ${d.rawBytes.size} B", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                            Text("${d.rssi}", color = MatrixGreen, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    } else {
        val structs = remember(dev.address, dev.rawBytes.size) { BleAdParser.parse(dev.rawBytes) }
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("AD-INSPEKTOR", dev.name ?: dev.address, action = { HeaderChip("‹ zurück") { sel = null } })
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    MatrixCard {
                        Text("MAC ${dev.address}", color = MatrixText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("Hersteller ${dev.manufacturer ?: "—"}", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("${dev.rawBytes.size} B roh · ${structs.size} AD-Strukturen", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
                items(structs) { s ->
                    AccentCard(StatusData) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.typeName, color = StatusData, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("0x%02X".format(s.type), color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                        val decoded = decodeAd(s)
                        if (decoded != null) Text(decoded, color = MatrixText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                        if (s.hex.isNotEmpty()) Text(s.hex, color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** Menschenlesbare Deutung gängiger AD-Typen. */
private fun decodeAd(s: AdStructure): String? = when (s.type) {
    0x08, 0x09 -> "\"" + String(s.value).filter { it.code in 32..126 } + "\""
    0x01 -> if (s.value.isNotEmpty()) "Flags 0x%02X".format(s.value[0].toInt() and 0xFF) else null
    0x0A -> if (s.value.isNotEmpty()) "${s.value[0].toInt()} dBm" else null
    0xFF -> if (s.value.size >= 2) "Company 0x%02X%02X".format(s.value[1].toInt() and 0xFF, s.value[0].toInt() and 0xFF) else null
    else -> null
}
