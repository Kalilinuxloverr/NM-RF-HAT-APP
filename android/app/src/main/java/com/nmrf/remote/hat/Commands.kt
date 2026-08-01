package com.nmrf.remote.hat

/** Verifizierte Bruce-1.16-CLI-Befehle. nRF24-Jammer/Spektrum sind UI-only -> per Nav-Pad öffnen. */
object Commands {
    data class Cmd(val label: String, val cmd: String)

    val palette = listOf(
        Cmd("info", "info"),
        Cmd("help", "help"),
        Cmd("SubGHz", "subghz"),
        Cmd("IR", "ir"),
        Cmd("NFC", "rfid"),
        Cmd("WiFi", "wifi"),
        Cmd("GPIO", "gpio"),
        Cmd("reboot", "reboot"),
    )

    // Bruce `nav <dir>`: gültige Richtungen = up/down/prev/next/select/esc
    val navUp = "nav up"
    val navDown = "nav down"
    val navLeft = "nav prev"    // links/zurück
    val navRight = "nav next"   // rechts/weiter
    val navSelect = "nav select"
    val navEsc = "nav esc"
}
