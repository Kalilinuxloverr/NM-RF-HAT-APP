package com.nmrf.remote.hat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nmrf.remote.ble.BleDevice
import com.nmrf.remote.ble.BleSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HatViewModel(private val link: BruceLink, private val scanner: BleSource) : ViewModel() {
    val state: StateFlow<LinkState> = link.state

    private val _scrollback = MutableStateFlow<List<String>>(emptyList())
    val scrollback: StateFlow<List<String>> = _scrollback.asStateFlow()

    private val _candidates = MutableStateFlow<List<BleDevice>>(emptyList())
    val candidates: StateFlow<List<BleDevice>> = _candidates.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch { link.lines.collect { append(it) } }
        viewModelScope.launch {
            link.state.collect { st -> if (st == LinkState.DISCONNECTED) startScan() else stopScan() }
        }
    }

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

    private fun stopScan() {
        scanJob?.cancel(); scanJob = null; _candidates.value = emptyList()
    }

    fun connect(address: String) { stopScan(); append("· verbinde $address …"); link.connect(address) }
    fun disconnect() { link.disconnect() }
    fun send(cmd: String) { if (cmd.isNotBlank()) { append("> $cmd"); link.send(cmd) } }

    private fun append(s: String) { _scrollback.value = (_scrollback.value + s).takeLast(500) }

    override fun onCleared() { link.disconnect() }
}
