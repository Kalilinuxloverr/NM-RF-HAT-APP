package com.nmrf.remote.detect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.StatusPill
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.ui.theme.StatusActive
import com.nmrf.remote.ui.theme.StatusAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QUICK = listOf("id", "getprop ro.product.model", "ip a", "ip route", "iw dev", "ping -c3 8.8.8.8")

/** Führt einen Befehl über sh bzw. su aus und liefert kombiniertes stdout/stderr. */
private fun exec(cmd: String, root: Boolean): String = try {
    val pb = if (root) ProcessBuilder("su", "-c", cmd) else ProcessBuilder("sh", "-c", cmd)
    pb.redirectErrorStream(true)
    val p = pb.start()
    val out = p.inputStream.bufferedReader().readText()
    p.waitFor()
    out.ifBlank { "(kein Output)" }
} catch (e: Exception) {
    "fehler: ${e.message}"
}

@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cmd by remember { mutableStateOf("") }
    var useRoot by remember { mutableStateOf(false) }
    var rootAvail by remember { mutableStateOf<Boolean?>(null) }
    var running by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        rootAvail = withContext(Dispatchers.IO) { exec("id", true).contains("uid=0") }
    }
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1) }

    fun run(c: String) {
        if (c.isBlank() || running) return
        running = true
        log.add("${if (useRoot) "#" else "$"} $c")
        scope.launch {
            val out = withContext(Dispatchers.IO) { exec(c, useRoot) }
            out.trimEnd().split("\n").forEach { log.add(it) }
            running = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("TERMINAL", if (running) "läuft…" else "On-Device-Shell", action = { HeaderChip("‹ SENTINEL", onBack) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderChip(if (useRoot) "ROOT: an" else "ROOT: aus") { useRoot = !useRoot }
            when (rootAvail) {
                true -> StatusPill("su ok", MatrixGreen)
                false -> StatusPill("kein su", StatusAlert)
                null -> StatusPill("prüfe…", StatusActive)
            }
            HeaderChip("clear") { log.clear() }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState) {
            items(log) { line ->
                val col = if (line.startsWith("$") || line.startsWith("#")) MatrixGreen else if (line.startsWith("fehler:")) StatusAlert else MatrixText
                Text(line, color = col, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK.take(3).forEach { q -> HeaderChip(q) { cmd = q; run(q) } }
        }
        OutlinedTextField(
            value = cmd,
            onValueChange = { cmd = it },
            label = { Text("Befehl") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            keyboardActions = KeyboardActions(onDone = { run(cmd) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixText, unfocusedTextColor = MatrixText,
                focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreenDark,
                focusedLabelColor = MatrixGreen, unfocusedLabelColor = MatrixTextDim, cursorColor = MatrixGreen,
            ),
        )
    }
}
