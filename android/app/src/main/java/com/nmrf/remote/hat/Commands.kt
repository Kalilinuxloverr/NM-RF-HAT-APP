package com.nmrf.remote.hat

/** Bruce-CLI-Befehle für Schnellzugriff. Exakte Sub-Verben mit `help` am Gerät prüfbar. */
object Commands {
    data class Cmd(val label: String, val cmd: String)

    val palette = listOf(
        Cmd("info", "info"),
        Cmd("help", "help"),
        Cmd("SubGHz", "subghz"),
        Cmd("IR", "ir"),
        Cmd("nRF24", "nrf24"),
        Cmd("WiFi", "wifi"),
        Cmd("BLE", "ble"),
        Cmd("RFID/NFC", "rfid"),
        Cmd("LED", "led"),
        Cmd("reboot", "reboot"),
    )

    val navUp = "nav Up"
    val navDown = "nav Down"
    val navLeft = "nav Left"
    val navRight = "nav Right"
    val navSelect = "nav Select"
    val navEsc = "nav Esc"
}
