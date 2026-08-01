package com.nmrf.remote.hat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED }

/** Naht zur HAT-Firmware: Text rein/raus (NUS) + Binär-Draw-Pakete (Screen-Mirror). */
interface BruceLink {
    val state: StateFlow<LinkState>
    val lines: Flow<String>
    val packets: Flow<ByteArray>   // 0xAA-Draw-Ops (tft_logger)
    fun connect(address: String, autoConnect: Boolean = false)
    fun disconnect()
    fun send(command: String)
}
