package com.nmrf.remote.hat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED }

/** Die einzige Naht zur HAT-Firmware: Text rein, Text raus (NUS über BLE). */
interface BruceLink {
    val state: StateFlow<LinkState>
    val lines: Flow<String>
    fun connect(address: String, autoConnect: Boolean = false)
    fun disconnect()
    fun send(command: String)
}
