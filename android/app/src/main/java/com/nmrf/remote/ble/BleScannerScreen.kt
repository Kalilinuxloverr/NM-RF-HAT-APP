package com.nmrf.remote.ble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.theme.NeonCyan
import com.nmrf.remote.ui.theme.NeonGreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun BleScannerScreen(
    vm: BleScannerViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    gattProbe: GattProbe,
) {
    val state by vm.state.collectAsState()
    var selectedAddr by rememberSaveable { mutableStateOf<String?>(null) }

    if (!hasPermission) {
        PermissionInfo(onRequestPermission)
        return
    }
    val selected = state.devices.firstOrNull { it.address == selectedAddr }
    if (selected != null) {
        BleDeviceDetail(selected, gattProbe, onBack = { selectedAddr = null })
    } else {
        BleList(state, vm, onSelect = { selectedAddr = it.address })
    }
}

@Composable
private fun BleList(state: BleUiState, vm: BleScannerViewModel, onSelect: (BleDevice) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = state.filter,
            onValueChange = vm::setFilter,
            label = { Text("Filter (Name / MAC / Hersteller)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("nur connectable", Modifier.weight(1f))
            Switch(checked = state.connectableOnly, onCheckedChange = vm::setConnectableOnly)
        }
        Text(
            "${state.devices.size} Geräte" + if (state.scanning) " · scannt…" else "",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.devices) { d -> BleRow(d, onClick = { onSelect(d) }) }
        }
    }
}

@Composable
private fun BleRow(d: BleDevice, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.name ?: "<unbekannt>", fontWeight = FontWeight.Medium)
            Text(
                d.address,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            val sub = buildString {
                d.manufacturer?.let { append(it); append(" · ") }
                append(if (d.connectable) "connectable" else "non-conn")
                if (d.serviceUuids.isNotEmpty()) append(" · ${d.serviceUuids.size} svc")
            }
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${d.rssi} dBm", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            SignalBar(d.rssi)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun SignalBar(rssi: Int) {
    val norm = ((rssi + 100f) / 70f).coerceIn(0f, 1f)
    val color = when {
        rssi >= -60 -> NeonGreen
        rssi >= -80 -> NeonCyan
        else -> Color(0xFFFF6B6B)
    }
    Box(
        Modifier.width(90.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.2f)),
    ) {
        Box(Modifier.fillMaxWidth(norm).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
    }
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

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onBack) { Text("‹ Zurück") }
        Text(d.name ?: "<unbekannt>", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(d.address, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(12.dp))

        Field("RSSI", "${d.rssi} dBm")
        Field("Connectable", if (d.connectable) "ja" else "nein")
        d.txPower?.let { Field("TX-Power", "$it dBm") }
        Field("Hersteller", d.manufacturer ?: d.companyId?.let { "Company-ID 0x%04X".format(it) } ?: "—")
        if (d.serviceUuids.isNotEmpty()) Field("Service-UUIDs", d.serviceUuids.joinToString("\n"))

        Spacer(Modifier.height(12.dp))
        Text("RSSI-Verlauf", fontWeight = FontWeight.SemiBold)
        RssiSparkline(
            d.rssiHistory,
            Modifier.fillMaxWidth().height(80.dp).padding(vertical = 6.dp),
        )

        Spacer(Modifier.height(8.dp))
        Text("Roh-Advertisement (${d.rawBytes.size} B)", fontWeight = FontWeight.SemiBold)
        Text(
            d.rawBytes.toHex().ifBlankHex(),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    gatt = GattState.Loading
                    val r = withTimeoutOrNull(8000) { gattProbe.enumerate(d.address) }
                    gatt = when {
                        r == null -> GattState.Error("Timeout (8 s) — Gerät nicht erreichbar oder lehnt ab")
                        r.isSuccess -> GattState.Success(r.getOrDefault(emptyList()))
                        else -> GattState.Error(r.exceptionOrNull()?.message ?: "Fehler")
                    }
                }
            },
            enabled = gatt != GattState.Loading,
        ) { Text(if (gatt == GattState.Loading) "verbinde…" else "GATT lesen") }

        when (val g = gatt) {
            is GattState.Error -> Text(g.message, color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 8.dp))
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
        Text("keine Services gelesen")
        return
    }
    services.forEach { s ->
        Text(s.uuid, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        s.chars.forEach { c ->
            Text(
                "  ${c.uuid}  [${c.properties.joinToString(",")}]",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.width(120.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value)
    }
}

@Composable
private fun RssiSparkline(history: List<Int>, modifier: Modifier) {
    val line = NeonGreen
    val axis = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Canvas(modifier) {
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        if (history.size < 2) return@Canvas
        val stepX = size.width / (history.size - 1)
        fun y(r: Int) = size.height * (1f - ((r + 100f) / 70f).coerceIn(0f, 1f))
        for (i in 1 until history.size) {
            drawLine(
                line,
                Offset((i - 1) * stepX, y(history[i - 1])),
                Offset(i * stepX, y(history[i])),
                3f,
            )
        }
    }
}

@Composable
private fun PermissionInfo(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bluetooth-Berechtigung nötig", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Für den BLE-Scan braucht die App die Bluetooth-/Standort-Berechtigung " +
                "(je nach Android-Version). Bluetooth muss eingeschaltet sein.",
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Berechtigung erteilen") }
    }
}

private fun ByteArray.toHex(): String =
    joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

private fun String.ifBlankHex(): String = ifBlank { "—" }
