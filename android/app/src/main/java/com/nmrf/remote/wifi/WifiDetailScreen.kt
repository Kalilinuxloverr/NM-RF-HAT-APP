package com.nmrf.remote.wifi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ui.components.HelpButton
import com.nmrf.remote.ui.components.InfoRow
import com.nmrf.remote.ui.components.MatrixCard
import com.nmrf.remote.ui.components.SectionLabel
import com.nmrf.remote.ui.components.WaterfallStrip
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixText

fun securityOf(caps: String): String = when {
    caps.contains("WPA3") || caps.contains("SAE") -> "WPA3"
    caps.contains("WPA2") || caps.contains("RSN") -> "WPA2"
    caps.contains("WPA") -> "WPA"
    caps.contains("WEP") -> "WEP"
    caps.contains("ESS") -> "Offen"
    else -> "?"
}

private fun bandLabel(b: Band) = when (b) {
    Band.GHZ_2_4 -> "2.4 GHz"; Band.GHZ_5 -> "5 GHz"; Band.GHZ_6 -> "6 GHz"; Band.UNKNOWN -> "?"
}

@Composable
fun WifiDetailScreen(ap: AccessPoint, history: List<Int>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text("‹ zurück") }
        Text(ap.ssid.ifBlank { "<hidden>" }, style = MaterialTheme.typography.headlineSmall, color = MatrixGreen)
        Spacer(Modifier.height(12.dp))

        MatrixCard {
            InfoRow("BSSID", ap.bssid)
            InfoRow("Hersteller", Ouis.vendor(ap.bssid) ?: "unbekannt")
            InfoRow("Sicherheit", securityOf(ap.capabilities))
            InfoRow("Band", bandLabel(ap.band))
            InfoRow("Kanal", ap.channel.toString())
            InfoRow("Breite", "${ap.widthMhz} MHz")
            InfoRow("Frequenz", "${ap.freqMhz} MHz")
            InfoRow("RSSI", "${ap.rssi} dBm")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("RSSI-WASSERFALL")
            Spacer(Modifier.width(8.dp))
            HelpButton(
                "Signalstärke (RSSI) über die Zeit. Jede Spalte = ein Wi-Fi-Scan (~alle 15–30 s), " +
                    "links alt → rechts neu. Helle/weiße Bereiche = stärkeres Signal, die Linie ist der Verlauf. " +
                    "Gitterlinien: −90/−70/−50/−30 dBm.",
            )
        }
        WaterfallStrip(history, Modifier.fillMaxWidth().height(120.dp))

        SectionLabel("CAPABILITIES")
        Text(ap.capabilities.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = MatrixText)
        Spacer(Modifier.height(24.dp))
    }
}
