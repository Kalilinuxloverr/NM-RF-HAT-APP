package com.nmrf.remote.hat

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ble.BleDevice
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.wifi.PermissionInfo

private const val HAT_HELP =
    "Verbindet per BLE (Nordic UART Service) mit der NM-RF-HAT-Firmware (Fork). Voraussetzung: " +
        "im Bruce-Menü 'BLE Remote' an. Danach: Terminal für beliebige CLI-Befehle, Nav-Pad steuert " +
        "die On-Screen-Menüs fern (SubGHz/IR/NFC/Jammer starten), Paletten-Chips senden Kurzbefehle. " +
        "Nav-/Sub-Verben ggf. mit 'help' am Gerät prüfen."

@Composable
fun HatScreen(vm: HatViewModel, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("HAT-STEUERUNG", "BLE-Fernsteuerung", help = HAT_HELP)
            PermissionInfo("Bluetooth-Berechtigung nötig", onRequestPermission)
        }
        return
    }
    val st by vm.state.collectAsState()
    when (st) {
        LinkState.CONNECTED -> ConnectedView(vm)
        LinkState.CONNECTING -> Column(Modifier.fillMaxSize()) {
            ScreenHeader("HAT-STEUERUNG", "verbinde…", help = HAT_HELP)
        }
        LinkState.DISCONNECTED -> ConnectView(vm)
    }
}

@Composable
private fun ConnectView(vm: HatViewModel) {
    val cands by vm.candidates.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("HAT-STEUERUNG", "nicht verbunden · suche NMRF-HAT", help = HAT_HELP)
        Column(Modifier.padding(16.dp)) {
            if (cands.isEmpty()) {
                Text("Kein HAT gefunden.", color = MatrixText)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Firmware flashen (Fork) und im Bruce-Menü 'BLE Remote' einschalten. " +
                        "Gerät wirbt als NMRF-HAT.",
                    style = MaterialTheme.typography.bodySmall, color = MatrixTextDim,
                )
            } else {
                Text("gefundene Geräte:", color = MatrixGreen)
                cands.forEach { d -> HatCandidate(d) { vm.connect(d.address) } }
            }
        }
    }
}

@Composable
private fun HatCandidate(d: BleDevice, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.name ?: "<unbekannt>", color = MatrixText)
            Text(d.address, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MatrixTextDim)
        }
        HeaderChip("VERBINDEN", onClick)
    }
}

@Composable
private fun ConnectedView(vm: HatViewModel) {
    val lines by vm.scrollback.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var stealthOff by remember { mutableStateOf(false) }

    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "HAT-STEUERUNG", "verbunden", help = HAT_HELP,
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderChip(if (stealthOff) "LICHT" else "STEALTH") { stealthOff = !stealthOff; vm.send(if (stealthOff) "stealth on" else "stealth off") }
                    Spacer(Modifier.width(8.dp))
                    HeaderChip("TRENNEN") { vm.disconnect() }
                }
            },
        )
        // Terminal
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            items(lines) { l ->
                val col = if (l.startsWith(">")) MatrixGreen else if (l.startsWith("·")) MatrixTextDim else MatrixText
                Text(l, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = col)
            }
        }
        // Eingabe
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                label = { Text("CLI-Befehl") },
                modifier = Modifier.weight(1f),
                keyboardActions = KeyboardActions(onSend = { vm.send(input); input = "" }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MatrixText, unfocusedTextColor = MatrixText,
                    focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreenDark,
                    focusedLabelColor = MatrixGreen, unfocusedLabelColor = MatrixTextDim, cursorColor = MatrixGreen,
                ),
            )
            Spacer(Modifier.width(8.dp))
            HeaderChip("SEND") { vm.send(input); input = "" }
        }
        // Befehls-Palette
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Commands.palette.forEach { c ->
                HeaderChip(c.label) { vm.send(c.cmd) }
                Spacer(Modifier.width(6.dp))
            }
        }
        // Nav-Pad
        NavPad(vm)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NavPad(vm: HatViewModel) {
    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NavBtn("▲") { vm.send(Commands.navUp) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavBtn("◀") { vm.send(Commands.navLeft) }
            Spacer(Modifier.width(8.dp))
            NavBtn("OK") { vm.send(Commands.navSelect) }
            Spacer(Modifier.width(8.dp))
            NavBtn("▶") { vm.send(Commands.navRight) }
        }
        NavBtn("▼") { vm.send(Commands.navDown) }
        Spacer(Modifier.height(6.dp))
        NavBtn("ESC") { vm.send(Commands.navEsc) }
    }
}

@Composable
private fun NavBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).clip(RoundedCornerShape(6.dp))
            .border(1.dp, MatrixGreenDark, RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = MatrixGreen, fontSize = 18.sp) }
}
