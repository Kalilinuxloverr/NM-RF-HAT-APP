package com.nmrf.remote.hat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ble.BleDevice
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.MatrixCard
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.SectionLabel
import com.nmrf.remote.ui.theme.MatrixBlack
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.wifi.PermissionInfo

private const val HAT_HELP =
    "BLE-Fernsteuerung der NM-RF-HAT-Firmware (NUS). BEFEHLE: gruppierte CLI-Kurzbefehle + " +
        "Stealth/Helligkeit + Nav-Pad (steuert die On-Screen-Menüs, z. B. nRF24-Jammer/Spektrum). " +
        "TERMINAL: freie Befehle + Antworten der Firmware. Befehle mit Argumenten (z. B. subghz tx) " +
        "im Terminal tippen."

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
                    "Firmware flashen und im Bruce-Menü 'BLE Remote' an lassen. Gerät wirbt als NMRF-HAT.",
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stealthOff by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "HAT-STEUERUNG", "verbunden", help = HAT_HELP,
            action = {
                HeaderChip(if (stealthOff) "LICHT" else "STEALTH") {
                    stealthOff = !stealthOff
                    vm.send(if (stealthOff) "stealth on" else "stealth off")
                }
                Spacer(Modifier.width(8.dp))
                HeaderChip("TRENNEN") { vm.disconnect() }
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            TabChip("BEFEHLE", tab == 0) { tab = 0 }
            Spacer(Modifier.width(8.dp))
            TabChip("TERMINAL", tab == 1) { tab = 1 }
        }
        if (tab == 0) ControlPanel(vm, stealthOff, onStealth = { on -> stealthOff = on; vm.send(if (on) "stealth on" else "stealth off") })
        else TerminalView(vm)
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MatrixGreenDark else MatrixPanel
    val fg = if (selected) MatrixGreen else MatrixTextDim
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(bg)
            .border(1.dp, MatrixGreenDark, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    ) { Text(label, color = fg, style = MaterialTheme.typography.labelLarge) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlPanel(vm: HatViewModel, stealthOff: Boolean, onStealth: (Boolean) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Commands.groups) { g ->
            MatrixCard {
                SectionLabel(g.title)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.cmds.forEach { c -> HeaderChip(c.label) { vm.send(c.cmd) } }
                }
            }
        }
        item {
            MatrixCard {
                SectionLabel("STEALTH / HELLIGKEIT")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderChip(if (stealthOff) "LICHT" else "STEALTH") { onStealth(!stealthOff) }
                    Commands.brightness.forEach { b -> HeaderChip("$b%") { vm.send("screen br $b") } }
                }
            }
        }
        item {
            MatrixCard {
                SectionLabel("NAV-PAD (Menüs · z. B. nRF24)")
                NavPad(vm)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun TerminalView(vm: HatViewModel) {
    val lines by vm.scrollback.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            items(lines) { l ->
                val col = if (l.startsWith(">")) MatrixGreen else if (l.startsWith("·")) MatrixTextDim else MatrixText
                Text(l, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = col)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, singleLine = true,
                label = { Text("CLI-Befehl") }, modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MatrixText, unfocusedTextColor = MatrixText,
                    focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreenDark,
                    focusedLabelColor = MatrixGreen, unfocusedLabelColor = MatrixTextDim, cursorColor = MatrixGreen,
                ),
            )
            Spacer(Modifier.width(8.dp))
            HeaderChip("SEND") { vm.send(input); input = "" }
        }
    }
}

@Composable
private fun NavPad(vm: HatViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
