package com.nmrf.remote.core

object Changelog {
    data class Entry(val version: String, val items: List<String>)

    val entries = listOf(
        Entry("Redesign (Phase 1)", listOf("Kachel-Launcher (Home)", "Settings + Changelog", "HAT: Mirror groß oben + Fadenkreuz/OK/ESC unten")),
        Entry("v2d", listOf("Tap-on-Mirror = Touch an die Firmware", "einstellbares nRF24-Jammer-PA (MIN/LOW/HIGH/MAX)", "alle Spektren als vertikale Wasserfälle")),
        Entry("v2c", listOf("CYD-Screen-Mirror über BLE (Draw-Command-Stream)")),
        Entry("v2b", listOf("nRF24-Spektrum-Wasserfall aktiviert + Live-Stream in die App")),
        Entry("v2a", listOf("Ausgabe-Echo HAT→App", "Stealth (Display aus)", "Auto-Reconnect", "echte Bruce-nav-Verben")),
        Entry("v2", listOf("HAT-Steuerung: BLE-Link (NUS), Terminal, Nav-Pad, Befehls-Palette")),
        Entry("v1", listOf("WLAN-Analyzer", "BLE-Recon + GATT", "Audio-Spektrogramm (FFT)", "Matrix-Theme + Boot-Anim + Logo", "Firmware-Fork Bruce 1.16 + BLE-Bridge")),
    )
}
