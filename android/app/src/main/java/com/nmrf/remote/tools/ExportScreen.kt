package com.nmrf.remote.tools

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ble.BleScanner
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.SectionLabel
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.wifi.WifiScanner

@Composable
fun ExportScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val wifi = remember { WifiScanner(ctx.applicationContext) }
    val ble = remember { BleScanner(ctx.applicationContext) }
    val aps by remember { wifi.results }.collectAsState(initial = emptyList())
    val devs by remember { ble.devices }.collectAsState(initial = emptyList())

    fun share(title: String, csv: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, csv)
        }
        ctx.startActivity(Intent.createChooser(i, title))
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HeaderChip("‹ TOOLS", onBack)
        SectionLabel("SCANS EXPORTIEREN (CSV teilen)")
        Text("WLAN: ${aps.size} Netze · BLE: ${devs.size} Geräte (aktueller Scan)", color = MatrixTextDim, style = MaterialTheme.typography.bodySmall)
        HeaderChip("WLAN-CSV teilen") {
            val csv = "ssid,bssid,freqMhz,rssi,channel,band,widthMhz,capabilities\n" +
                aps.joinToString("\n") { "\"${it.ssid}\",${it.bssid},${it.freqMhz},${it.rssi},${it.channel},${it.band},${it.widthMhz},\"${it.capabilities}\"" }
            share("nmrf-wifi.csv", csv)
        }
        HeaderChip("BLE-CSV teilen") {
            val csv = "name,address,rssi,connectable,companyId,manufacturer,services\n" +
                devs.joinToString("\n") { "\"${it.name ?: ""}\",${it.address},${it.rssi},${it.connectable},${it.companyId ?: ""},\"${it.manufacturer ?: ""}\",${it.serviceUuids.size}" }
            share("nmrf-ble.csv", csv)
        }
        Text("Teilt den aktuellen Scan als CSV (E-Mail/Drive/…). Kein Speichern auf Datei nötig.", color = MatrixTextDim, style = MaterialTheme.typography.bodySmall)
    }
}
