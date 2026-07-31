package com.nmrf.remote.ble

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.components.InfoRow
import com.nmrf.remote.ui.components.MatrixCard
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.SectionLabel
import com.nmrf.remote.ui.components.SignalBar
import com.nmrf.remote.ui.components.WaterfallStrip
import com.nmrf.remote.ui.theme.MatrixBlack
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixRed
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.wifi.PermissionInfo
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val BLE_HELP =
    "Passiver BLE-Scan: alle Advertisements in Reichweite. Pro Gerät MAC, Name, Hersteller " +
        "(aus der Company-ID), Signal (RSSI) und ob es connectable ist. Filter oben nach Name/MAC/Hersteller, " +
        "Schalter nur-connectable. Gerät antippen → Details, RSSI-Wasserfall und GATT-Tabelle " +
        "(Services/Characteristics) beim Verbinden."

@Composable
fun BleScannerScreen(
    vm: BleScannerViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    gattProbe: GattProbe,
) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("BLE-SCANNER", "Bluetooth Low Energy", help = BLE_HELP)
            PermissionInfo("Bluetooth-Berechtigung nötig", onRequestPermission)
        }
        return
    }
    val state by vm.state.collectAsState()
    var selectedAddr by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.devices.firstOrNull { it.address == selectedAddr }
    if (selected != null) {
        BleDeviceDetail(selected, gattProbe, onBack = { selectedAddr = null })
    } else {
        BleList(state, vm, onSelect = { selectedAddr = it.address })
    }
}

@Composable
private fun BleList(state: BleUiState, vm: BleScannerViewModel, onSelect: (BleDevice) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "BLE-SCANNER",
            "${state.devices.size} Geräte · ${if (state.scanning) "scannt…" else "bereit"}",
            help = BLE_HELP,
        )
        Column(Modifier.padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = state.filter,
                onValueChange = vm::setFilter,
                label = { Text("Filter: Name / MAC / Hersteller") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MatrixText,
                    unfocusedTextColor = MatrixText,
                    focusedBorderColor = MatrixGreen,
                    unfocusedBorderColor = MatrixGreenDark,
                    focusedLabelColor = MatrixGreen,
                    unfocusedLabelColor = MatrixTextDim,
                    cursorColor = MatrixGreen,
                ),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("nur connectable", Modifier.weight(1f), color = MatrixText)
                Switch(
                    checked = state.connectableOnly,
                    onCheckedChange = vm::setConnectableOnly,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MatrixBlack,
                        checkedTrackColor = MatrixGreen,
                        uncheckedThumbColor = MatrixTextDim,
                        uncheckedTrackColor = MatrixGreenDark,
                    ),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.devices) { d -> BleRow(d, onClick = { onSelect(d) }) }
            }
        }
    }
}

@Composable
private fun BleRow(d: BleDevice, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.name ?: "<unbekannt>", color = MatrixText, fontWeight = FontWeight.Medium)
            Text(d.address, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MatrixTextDim)
            val sub = buildString {
                d.manufacturer?.let { append(it); append(" · ") }
                append(if (d.connectable) "connectable" else "non-conn")
                if (d.serviceUuids.isNotEmpty()) append(" · ${d.serviceUuids.size} svc")
            }
            Text(sub, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MatrixTextDim)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${d.rssi}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MatrixGreen)
            Spacer(Modifier.height(3.dp))
            SignalBar(d.rssi)
        }
    }
    HorizontalDivider(color = MatrixGreenDark.copy(alpha = 0.5f))
}

private sealed interface GattState {
    data object Idle : GattState
    data object Loading : GattState
    data class Success(val services: List<GattService>) : GattState
    data class Error(val message: String) : GattState
}

@Composable
private fun BleDeviceDetail(d: BleDevice, gattProbe: GattProbe, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var gatt by remember(d.address) { mutableStateOf<GattState>(GattState.Idle) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text("‹ zurück") }
        Text(d.name ?: "<unbekannt>", style = MaterialTheme.typography.headlineSmall, color = MatrixGreen)
        Spacer(Modifier.height(12.dp))

        MatrixCard {
            InfoRow("MAC", d.address)
            InfoRow("Hersteller", d.manufacturer ?: d.companyId?.let { "0x%04X".format(it) } ?: "—")
            InfoRow("RSSI", "${d.rssi} dBm")
            InfoRow("Connectable", if (d.connectable) "ja" else "nein")
            d.txPower?.let { InfoRow("TX-Power", "$it dBm") }
            if (d.serviceUuids.isNotEmpty()) InfoRow("Services", "${d.serviceUuids.size}")
        }

        SectionLabel("RSSI-WASSERFALL")
        WaterfallStrip(d.rssiHistory, Modifier.fillMaxWidth().height(100.dp))

        if (d.serviceUuids.isNotEmpty()) {
            SectionLabel("SERVICE-UUIDs (Advertisement)")
            d.serviceUuids.forEach {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MatrixText)
            }
        }

        SectionLabel("ROH-ADVERTISEMENT (${d.rawBytes.size} B)")
        Text(d.rawBytes.toHex(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MatrixTextDim)

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    gatt = GattState.Loading
                    val r = withTimeoutOrNull(8000) { gattProbe.enumerate(d.address) }
                    gatt = when {
                        r == null -> GattState.Error("Timeout (8 s) — nicht erreichbar oder lehnt ab")
                        r.isSuccess -> GattState.Success(r.getOrDefault(emptyList()))
                        else -> GattState.Error(r.exceptionOrNull()?.message ?: "Fehler")
                    }
                }
            },
            enabled = gatt != GattState.Loading,
        ) { Text(if (gatt == GattState.Loading) "verbinde…" else "GATT lesen") }

        when (val g = gatt) {
            is GattState.Error -> Text(g.message, color = MatrixRed, modifier = Modifier.padding(top = 8.dp))
            is GattState.Success -> GattTable(g.services)
            else -> {}
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GattTable(services: List<GattService>) {
    Spacer(Modifier.height(8.dp))
    if (services.isEmpty()) {
        Text("keine Services gelesen", color = MatrixText)
        return
    }
    services.forEach { s ->
        Text(
            s.uuid,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MatrixGreen,
            modifier = Modifier.padding(top = 8.dp),
        )
        s.chars.forEach { c ->
            Text(
                "  ${c.uuid}  [${c.properties.joinToString(",")}]",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MatrixTextDim,
            )
        }
    }
}

private fun ByteArray.toHex(): String =
    if (isEmpty()) "—" else joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
