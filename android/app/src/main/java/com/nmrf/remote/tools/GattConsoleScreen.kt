package com.nmrf.remote.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ble.BleScanner
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.MatrixCard
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.SectionLabel
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.wifi.PermissionInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GattConsoleScreen(onBack: () -> Unit, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    val ctx = LocalContext.current
    val client = remember { GattClient(ctx) }
    DisposableEffect(Unit) { onDispose { client.disconnect() } }

    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("GATT-KONSOLE", "BLE", action = { HeaderChip("‹ TOOLS", onBack) })
            PermissionInfo("Bluetooth-Berechtigung nötig", onRequestPermission)
        }
        return
    }
    val st by client.state.collectAsState()
    if (st == "disconnected") {
        val scanner = remember { BleScanner(ctx.applicationContext) }
        val devs by remember { scanner.devices }.collectAsState(initial = emptyList())
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("GATT-KONSOLE", "Gerät wählen", action = { HeaderChip("‹ TOOLS", onBack) })
            LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                items(devs.filter { it.connectable }.sortedByDescending { it.rssi }) { d ->
                    Row(Modifier.fillMaxWidth().clickable { client.connect(d.address) }.padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name ?: "<unbekannt>", color = MatrixText)
                            Text("${d.address} · ${d.rssi} dBm", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        HeaderChip("CONNECT") { client.connect(d.address) }
                    }
                }
            }
        }
        return
    }

    val svcs by client.services.collectAsState()
    val log by client.log.collectAsState()
    var hex by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("GATT-KONSOLE", st, action = { HeaderChip("TRENNEN") { client.disconnect() } })
        OutlinedTextField(
            value = hex, onValueChange = { hex = it }, singleLine = true, label = { Text("Schreib-Hex (z. B. 01FF)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixText, unfocusedTextColor = MatrixText, focusedBorderColor = MatrixGreen,
                unfocusedBorderColor = MatrixGreenDark, focusedLabelColor = MatrixGreen, unfocusedLabelColor = MatrixTextDim, cursorColor = MatrixGreen,
            ),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            items(svcs) { s ->
                MatrixCard(Modifier.padding(bottom = 8.dp)) {
                    Text("svc ${s.uuid}", color = MatrixGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    s.chars.forEach { c ->
                        Text("  ${c.uuid} [${c.props.joinToString(",")}]", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (c.read) HeaderChip("READ") { client.read(c.uuid) }
                            if (c.notify) HeaderChip("NOTIFY") { client.setNotify(c.uuid, true) }
                            if (c.notify) HeaderChip("N-STOP") { client.setNotify(c.uuid, false) }
                            if (c.write) HeaderChip("WRITE") { client.write(c.uuid, hex) }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
        SectionLabel("LOG")
        LazyColumn(Modifier.height(140.dp).fillMaxWidth().padding(horizontal = 12.dp)) {
            items(log.reversed()) { Text(it, color = MatrixText, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        }
    }
}
