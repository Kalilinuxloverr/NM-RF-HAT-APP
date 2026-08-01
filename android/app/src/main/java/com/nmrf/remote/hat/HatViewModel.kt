package com.nmrf.remote.hat

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nmrf.remote.ble.BleDevice
import com.nmrf.remote.ble.BleSource
import com.nmrf.remote.core.AppPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HatViewModel(
    private val link: BruceLink,
    private val scanner: BleSource,
    private val prefs: AppPrefs,
    private val wifi: WifiMirror,
) : ViewModel() {
    val state: StateFlow<LinkState> = link.state

    private val _scrollback = MutableStateFlow<List<String>>(emptyList())
    val scrollback: StateFlow<List<String>> = _scrollback.asStateFlow()
    private val _candidates = MutableStateFlow<List<BleDevice>>(emptyList())
    val candidates: StateFlow<List<BleDevice>> = _candidates.asStateFlow()

    private val _spectrum = MutableStateFlow<List<FloatArray>>(emptyList())
    val spectrum: StateFlow<List<FloatArray>> = _spectrum.asStateFlow()
    private val specCols = ArrayDeque<FloatArray>()

    private val replay = TftReplay()
    private val _frame = MutableStateFlow(0)
    val frame: StateFlow<Int> = _frame.asStateFlow()
    fun screenBitmap(): Bitmap? = replay.bmp

    private val _wifiActive = MutableStateFlow(false)
    val wifiActive: StateFlow<Boolean> = _wifiActive.asStateFlow()
    private var wifiJob: Job? = null

    private var scanJob: Job? = null
    private var reconnectJob: Job? = null
    private var userDisconnected = false

    init {
        viewModelScope.launch { link.lines.collect { onLine(it) } }
        viewModelScope.launch { link.packets.collect { replay.apply(it); _frame.value = _frame.value + 1 } }
        viewModelScope.launch { link.state.collect { onState(it) } }
        prefs.lastHat?.takeIf { it.isNotBlank() }?.let {
            append("· Auto-Reconnect: $it")
            link.connect(it, autoConnect = true)
        }
    }

    // --- Mirror (BLE-Standard) / WLAN-Umschaltung ---
    fun mirrorEnter() { if (!_wifiActive.value) link.send("mirror on") }
    fun mirrorLeave() { link.send("mirror off"); if (_wifiActive.value) stopWifiMirror() }

    fun startWifiMirror() {
        if (_wifiActive.value) return
        link.send("mirror off")
        link.send("webon")
        append("· WLAN: dem AP 'NMRF-HAT' beitreten…")
        wifiJob = viewModelScope.launch {
            delay(600)
            if (wifi.start()) {
                _wifiActive.value = true
                append("· WLAN-Mirror aktiv (172.0.0.1)")
                wifi.packets().collect { replay.apply(it); _frame.value = _frame.value + 1 }
            } else {
                append("· WLAN fehlgeschlagen — bleibe bei BLE")
                link.send("weboff"); link.send("mirror on")
            }
        }
    }

    fun stopWifiMirror() {
        wifiJob?.cancel(); wifiJob = null
        wifi.stop()
        _wifiActive.value = false
        link.send("weboff")
    }

    private fun onLine(l: String) {
        if (l.startsWith("SPEC:")) {
            val vals = l.removePrefix("SPEC:").split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (vals.isNotEmpty()) {
                val col = FloatArray(vals.size) { (vals[it] / 100f).coerceIn(0f, 1f) }
                specCols.addLast(col); while (specCols.size > 120) specCols.removeFirst()
                _spectrum.value = specCols.toList()
            }
        } else append(l)
    }

    private fun onState(st: LinkState) {
        when (st) {
            LinkState.CONNECTED -> { userDisconnected = false; cancelReconnect(); stopScan() }
            LinkState.CONNECTING -> stopScan()
            LinkState.DISCONNECTED ->
                if (!userDisconnected && prefs.autoReconnect && !prefs.lastHat.isNullOrBlank()) scheduleReconnect() else startScan()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob != null) return
        reconnectJob = viewModelScope.launch {
            delay(2500); reconnectJob = null
            val addr = prefs.lastHat
            if (!userDisconnected && !addr.isNullOrBlank()) link.connect(addr, autoConnect = true)
        }
    }
    private fun cancelReconnect() { reconnectJob?.cancel(); reconnectJob = null }

    private fun startScan() {
        if (scanJob != null) return
        scanJob = viewModelScope.launch {
            scanner.devices.collect { list ->
                _candidates.value = list.filter {
                    (it.name?.contains("NMRF", true) == true) ||
                        it.serviceUuids.any { u -> u.startsWith("6e400001", ignoreCase = true) }
                }
            }
        }
    }
    private fun stopScan() { scanJob?.cancel(); scanJob = null; _candidates.value = emptyList() }

    fun connect(address: String) {
        userDisconnected = false; prefs.lastHat = address
        cancelReconnect(); stopScan(); append("· verbinde $address …")
        link.connect(address, autoConnect = false)
    }

    fun disconnect() { userDisconnected = true; cancelReconnect(); if (_wifiActive.value) stopWifiMirror(); link.disconnect() }
    fun send(cmd: String) { if (cmd.isNotBlank()) { append("> $cmd"); link.send(cmd) } }

    private fun append(s: String) { _scrollback.value = (_scrollback.value + s).takeLast(500) }

    override fun onCleared() { if (_wifiActive.value) stopWifiMirror(); link.disconnect() }
}
