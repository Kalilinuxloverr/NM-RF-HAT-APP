package com.nmrf.remote.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val visible: List<AccessPoint>,
    val selectedBand: Band,
    val scanning: Boolean,
)

class WifiAnalyzerViewModel(private val source: WifiSource) : ViewModel() {
    private val raw = MutableStateFlow(source.latest())
    private val band = MutableStateFlow(Band.GHZ_2_4)
    private val scanning = MutableStateFlow(false)

    val state: StateFlow<UiState> = combine(raw, band, scanning) { aps, b, sc ->
        UiState(aps.filter { it.band == b }.sortedByDescending { it.rssi }, b, sc)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState(emptyList(), Band.GHZ_2_4, false))

    init {
        viewModelScope.launch { source.results.collect { raw.value = it } }
    }

    fun selectBand(b: Band) { band.value = b }

    fun rescan() { scanning.value = source.requestScan() }
}
