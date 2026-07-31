package com.nmrf.remote.ble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BleUiState(
    val devices: List<BleDevice>,
    val filter: String,
    val connectableOnly: Boolean,
    val scanning: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BleScannerViewModel(private val source: BleSource) : ViewModel() {
    private val enabled = MutableStateFlow(false)
    private val raw = MutableStateFlow<List<BleDevice>>(emptyList())
    private val filter = MutableStateFlow("")
    private val connectableOnly = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            enabled.flatMapLatest { on -> if (on) source.devices else flowOf(emptyList()) }
                .collect { raw.value = it }
        }
    }

    val state: StateFlow<BleUiState> =
        combine(raw, filter, connectableOnly, enabled) { list, f, cOnly, on ->
            val visible = list.asSequence()
                .filter { !cOnly || it.connectable }
                .filter { f.isBlank() || it.matches(f) }
                .sortedByDescending { it.rssi }
                .toList()
            BleUiState(visible, f, cOnly, on)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BleUiState(emptyList(), "", false, false))

    fun setEnabled(b: Boolean) { enabled.value = b }
    fun setFilter(f: String) { filter.value = f }
    fun setConnectableOnly(b: Boolean) { connectableOnly.value = b }
}

private fun BleDevice.matches(query: String): Boolean {
    val q = query.lowercase()
    return address.lowercase().contains(q) ||
        (name?.lowercase()?.contains(q) == true) ||
        (manufacturer?.lowercase()?.contains(q) == true)
}
