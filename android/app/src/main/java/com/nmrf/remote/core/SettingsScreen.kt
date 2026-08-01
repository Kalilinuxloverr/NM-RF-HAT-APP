package com.nmrf.remote.core

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.InfoRow
import com.nmrf.remote.ui.components.MatrixCard
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.SectionLabel
import com.nmrf.remote.ui.theme.MatrixBlack
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { AppPrefs(ctx) }
    var transport by remember { mutableStateOf(prefs.transport) }
    var autoRe by remember { mutableStateOf(prefs.autoReconnect) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("SETTINGS", "App-Einstellungen & Changelog", action = { HeaderChip("‹ HOME", onBack) })
        Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
            SectionLabel("MIRROR-TRANSPORT")
            MatrixCard {
                Row {
                    listOf("auto" to "Auto", "wifi" to "WLAN", "ble" to "BLE").forEach { (v, l) ->
                        HeaderChip(if (transport == v) "● $l" else l) { transport = v; prefs.transport = v }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(
                    "Auto: WLAN übers CYD-AP wenn verbunden, sonst BLE. (WLAN-Mirror kommt in Phase 2.)",
                    style = MaterialTheme.typography.bodySmall, color = MatrixTextDim,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionLabel("VERBINDUNG")
            MatrixCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-Reconnect zum HAT", Modifier.weight(1f), color = MatrixText)
                    Switch(
                        checked = autoRe, onCheckedChange = { autoRe = it; prefs.autoReconnect = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MatrixBlack, checkedTrackColor = MatrixGreen,
                            uncheckedThumbColor = MatrixTextDim, uncheckedTrackColor = MatrixGreenDark,
                        ),
                    )
                }
            }

            SectionLabel("CYD-WLAN (AP)")
            MatrixCard {
                InfoRow("SSID", "NMRF-HAT")
                InfoRow("Passwort", "nmrflab1")
                Text(
                    "Firmware spannt dieses AP auf; die App tritt für den WLAN-Mirror bei (Phase 2).",
                    style = MaterialTheme.typography.bodySmall, color = MatrixTextDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SectionLabel("CHANGELOG")
            Changelog.entries.forEach { e ->
                MatrixCard(Modifier.padding(bottom = 8.dp)) {
                    Text(e.version, color = MatrixGreen, fontFamily = FontFamily.Monospace)
                    e.items.forEach { Text("› $it", color = MatrixText, style = MaterialTheme.typography.bodySmall) }
                }
            }

            SectionLabel("ÜBER")
            MatrixCard {
                InfoRow("Gerät", "NM-RF-HAT · CYD (ESP32-2432S028)")
                InfoRow("Firmware", "Bruce 1.16 Fork + BLE-Bridge")
                InfoRow("Repo", "github.com/Kalilinuxloverr/NM-RF-HAT-APP")
                Text("Nur für autorisierten Laborgebrauch.", style = MaterialTheme.typography.bodySmall, color = MatrixTextDim, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
