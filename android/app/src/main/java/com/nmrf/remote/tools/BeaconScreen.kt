package com.nmrf.remote.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ble.BleDevice
import com.nmrf.remote.ble.BleScanner
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.MatrixCard
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim

private fun decode(d: BleDevice): String? {
    val b = d.rawBytes
    var i = 0
    while (i + 1 < b.size) {
        val len = b[i].toInt() and 0xFF
        if (len == 0) break
        val type = b[i + 1].toInt() and 0xFF
        val start = i + 2; val end = (i + 1 + len).coerceAtMost(b.size)
        if (type == 0xFF && end - start >= 4) {
            val cid = (b[start].toInt() and 0xFF) or ((b[start + 1].toInt() and 0xFF) shl 8)
            if (cid == 0x004C && (b[start + 2].toInt() and 0xFF) == 0x02 && (b[start + 3].toInt() and 0xFF) == 0x15 && end - start >= 25) {
                val uuid = (start + 4 until start + 20).joinToString("") { "%02X".format(b[it]) }
                val major = ((b[start + 20].toInt() and 0xFF) shl 8) or (b[start + 21].toInt() and 0xFF)
                val minor = ((b[start + 22].toInt() and 0xFF) shl 8) or (b[start + 23].toInt() and 0xFF)
                return "iBeacon\nUUID $uuid\nmajor $major · minor $minor"
            }
        }
        if (type == 0x16 && end - start >= 2) {
            val svc = (b[start].toInt() and 0xFF) or ((b[start + 1].toInt() and 0xFF) shl 8)
            if (svc == 0xFEAA) return "Eddystone (${end - start - 2} B Frame)"
        }
        i += len + 1
    }
    return null
}

@Composable
fun BeaconScreen(onBack: () -> Unit, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    val ctx = LocalContext.current
    val scanner = remember { BleScanner(ctx.applicationContext) }
    val devices by remember { scanner.devices }.collectAsState(initial = emptyList())
    val beacons = remember(devices) { devices.mapNotNull { d -> decode(d)?.let { d to it } }.sortedByDescending { it.first.rssi } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("BLE-BEACON-DECODER", "${beacons.size} Beacons · iBeacon/Eddystone", action = { HeaderChip("‹ TOOLS", onBack) })
        if (!hasPermission) {
            Column(Modifier.padding(24.dp)) { HeaderChip("Bluetooth-Berechtigung", onRequestPermission) }
            return
        }
        LaunchedEffect(Unit) {}
        LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
            items(beacons) { (d, info) ->
                MatrixCard(Modifier.padding(bottom = 8.dp)) {
                    androidx.compose.material3.Text(d.name ?: d.address, color = MatrixGreen)
                    androidx.compose.material3.Text("${d.rssi} dBm · ${d.manufacturer ?: "?"}", color = MatrixTextDim, fontSize = 12.sp)
                    androidx.compose.material3.Text(info, color = MatrixText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}
