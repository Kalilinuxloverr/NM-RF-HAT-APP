package com.nmrf.remote.hat

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
) : ViewModel() {
    val state: StateFlow<LinkState> = link.state

    private val _scrollback = MutableStateFlow<List<String>>(emptyList())
    val scrollback: StateFlow<List<String>> = _scrollback.asStateFlow()
    private val _candidates = MutableStateFlow<List<BleDevice>>(emptyList())
    val candidates: StateFlow<List<BleDevice>> = _candidates.asStateFlow()

    private var scanJob: Job? = null
    private var reconnectJob: Job? = null
    private var userDisconnected = false

    init {
        viewModelScope.launch { link.lines.collect { append(it) } }
        viewModelScope.launch { link.state.collect { onState(it) } }
        prefs.lastHat?.takeIf { it.isNotBlank() }?.let {
            append("· Auto-Reconnect: $it")
            link.connect(it, autoConnect = true)
        }
    }

    private fun onState(st: LinkState) {
        when (st) {
            LinkState.CONNECTED -> { userDisconnected = false; cancelReconnect(); stopScan() }
            LinkState.CONNECTING -> stopScan()
            LinkState.DISCONNECTED ->
                if (!userDisconnected && !prefs.lastHat.isNullOrBlank()) scheduleReconnect() else startScan()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob != null) return
        reconnectJob = viewModelScope.launch {
            delay(2500)
            reconnectJob = null
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
        userDisconnected = false
        prefs.lastHat = address
        cancelReconnect(); stopScan()
        append("· verbinde $address …")
        link.connect(address, autoConnect = false)
    }

    fun disconnect() { userDisconnected = true; cancelReconnect(); link.disconnect() }
    fun forget() { prefs.lastHat = null; userDisconnected = true; cancelReconnect(); link.disconnect() }
    fun send(cmd: String) { if (cmd.isNotBlank()) { append("> $cmd"); link.send(cmd) } }

    private fun append(s: String) { _scrollback.value = (_scrollback.value + s).takeLast(500) }

    override fun onCleared() { link.disconnect() }
}
