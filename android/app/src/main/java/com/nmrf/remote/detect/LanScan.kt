package com.nmrf.remote.detect

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateListOf
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
import com.nmrf.remote.ui.components.StatusPill
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.ui.theme.StatusActive
import com.nmrf.remote.ui.theme.StatusData
import com.nmrf.remote.ui.theme.StatusOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

// ---------------------------------------------------------------------------
// Reine Netz-Mathematik (testbar)
// ---------------------------------------------------------------------------

data class LanHost(val ip: String, val mac: String?, val name: String?, val openPorts: List<Int>)

object Net {
    /** DhcpInfo liefert little-endian — hier zu "a.b.c.d". */
    fun leIntToIp(le: Int): String =
        "${le and 0xff}.${le shr 8 and 0xff}.${le shr 16 and 0xff}.${le shr 24 and 0xff}"

    /** Alle /24-Host-Adressen (.1 – .254) zur gegebenen IP. */
    fun slash24Hosts(ip: String): List<String> {
        val base = ip.substringBeforeLast('.', "")
        if (base.isBlank()) return emptyList()
        return (1..254).map { "$base.$it" }
    }

    fun serviceName(port: Int): String = when (port) {
        21 -> "ftp"; 22 -> "ssh"; 23 -> "telnet"; 25 -> "smtp"; 53 -> "dns"
        80 -> "http"; 110 -> "pop3"; 139 -> "netbios"; 143 -> "imap"; 443 -> "https"
        445 -> "smb"; 554 -> "rtsp"; 993 -> "imaps"; 1883 -> "mqtt"; 3389 -> "rdp"
        5353 -> "mdns"; 8080 -> "http-alt"; 8443 -> "https-alt"; 9100 -> "printer"; 62078 -> "iphone"
        else -> "?"
    }

    val LIVENESS_PORTS = intArrayOf(80, 443, 22, 445, 139, 53, 8080, 23)
    val SCAN_PORTS = intArrayOf(21, 22, 23, 25, 53, 80, 110, 139, 143, 443, 445, 554, 993, 1883, 3389, 5353, 8080, 8443, 9100, 62078)
}

// ---------------------------------------------------------------------------
// Scanner (Sockets, kein Root)
// ---------------------------------------------------------------------------

class LanScanner(context: Context) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    @Suppress("DEPRECATION")
    fun localIp(): String? = wifi?.dhcpInfo?.ipAddress?.takeIf { it != 0 }?.let { Net.leIntToIp(it) }

    /** Host lebt, wenn ein Port verbindet ODER aktiv ablehnt (RST). */
    suspend fun probe(ip: String): LanHost? = withContext(Dispatchers.IO) {
        var up = false
        val open = ArrayList<Int>()
        for (p in Net.LIVENESS_PORTS) {
            try {
                Socket().use { it.connect(InetSocketAddress(ip, p), 250); up = true; open.add(p) }
            } catch (e: java.net.ConnectException) {
                up = true                    // "connection refused" = Host da
            } catch (e: Exception) { /* Timeout/gefiltert */ }
        }
        if (!up) return@withContext null
        val name = runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrNull()?.takeIf { it != ip }
        LanHost(ip, arpTable()[ip], name, open.sorted())
    }

    suspend fun scanPorts(ip: String): List<Int> = withContext(Dispatchers.IO) {
        Net.SCAN_PORTS.toList().map { p ->
            async {
                try { Socket().use { it.connect(InetSocketAddress(ip, p), 400) }; p } catch (e: Exception) { null }
            }
        }.awaitAll().filterNotNull().sorted()
    }

    private fun arpTable(): Map<String, String> = runCatching {
        File("/proc/net/arp").readLines().drop(1).mapNotNull { line ->
            val c = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (c.size >= 4 && c[3] != "00:00:00:00:00:00") c[0] to c[3] else null
        }.toMap()
    }.getOrDefault(emptyMap())
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun LanScanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scanner = remember { LanScanner(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val hosts = remember { mutableStateListOf<LanHost>() }
    var scanning by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf<LanHost?>(null) }
    var selPorts by remember { mutableStateOf<List<Int>?>(null) }
    val myIp = remember { scanner.localIp() }

    fun start() {
        val targets = myIp?.let { Net.slash24Hosts(it) } ?: emptyList()
        if (targets.isEmpty()) return
        hosts.clear(); done = 0; total = targets.size; scanning = true
        scope.launch {
            val sem = Semaphore(64)
            coroutineScope {
                targets.map { ip ->
                    async {
                        sem.withPermit {
                            val h = scanner.probe(ip)
                            if (h != null) hosts.add(h)
                            done++
                        }
                    }
                }.awaitAll()
            }
            hosts.sortBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
            scanning = false
        }
    }

    val detail = sel
    if (detail != null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("HOST", detail.ip, action = { HeaderChip("‹ zurück") { sel = null; selPorts = null } })
            Column(Modifier.padding(12.dp)) {
                MatrixCard {
                    Text("IP   ${detail.ip}", color = MatrixText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text("MAC  ${detail.mac ?: "—"}", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text("Name ${detail.name ?: "—"}", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Row(Modifier.padding(top = 10.dp)) {
                    HeaderChip(if (selPorts == null) "Ports scannen" else "erneut scannen") {
                        selPorts = emptyList()
                        scope.launch { selPorts = scanner.scanPorts(detail.ip) }
                    }
                }
                selPorts?.let { ports ->
                    if (ports.isEmpty()) Text("scanne… / keine offenen Ports", color = MatrixTextDim, modifier = Modifier.padding(top = 10.dp))
                    else LazyColumn(Modifier.padding(top = 8.dp)) {
                        items(ports) { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                StatusPill("$p", StatusOk)
                                Text("  ${Net.serviceName(p)}", color = MatrixText, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("LAN-SCANNER", myIp?.let { "du: $it/24" } ?: "kein WLAN", action = { HeaderChip("‹ SENTINEL", onBack) })
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Hosts", "${hosts.size}", accent = MatrixGreen, modifier = Modifier.weight(1f))
            MetricTile("Geprüft", "$done", unit = "/$total", accent = StatusData, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 12.dp)) {
            if (myIp == null) StatusPill("Mit WLAN verbinden", StatusActive)
            else HeaderChip(if (scanning) "scannt… $done/$total" else "⌖ Netz scannen") { if (!scanning) start() }
        }
        if (hosts.isEmpty()) {
            EmptyState("🖧", if (scanning) "Suche Hosts…" else "LAN-Scanner", "Findet alle erreichbaren Geräte in deinem WLAN-Subnetz (/24), zeigt MAC & offene Ports. Host antippen → voller Port-Scan.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hosts) { h ->
                    AccentCard(StatusOk, onClick = { sel = h; selPorts = null }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.ip, color = MatrixText, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(h.name ?: (h.mac ?: "—"), color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                            if (h.openPorts.isNotEmpty()) StatusPill("${h.openPorts.size} offen", StatusData)
                        }
                        if (h.openPorts.isNotEmpty()) {
                            Text(h.openPorts.joinToString(" ") { "$it/${Net.serviceName(it)}" }, color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
    }
}
