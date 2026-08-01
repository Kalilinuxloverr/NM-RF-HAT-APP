package com.nmrf.remote.hat

/** Verifizierte Bruce-1.16-CLI-Befehle, nach Bereichen gruppiert.
 *  nRF24-Jammer/Spektrum sind UI-only -> per Nav-Pad öffnen. */
object Commands {
    data class Cmd(val label: String, val cmd: String)
    data class Group(val title: String, val cmds: List<Cmd>)

    val groups = listOf(
        Group("SYSTEM", listOf(
            Cmd("info", "info"), Cmd("uptime", "uptime"), Cmd("free", "free"),
            Cmd("help", "help"), Cmd("reboot", "reboot"),
            Cmd("poweroff", "poweroff"), Cmd("sleep", "sleep"),
        )),
        Group("SUBGHZ / RF", listOf(
            Cmd("rx", "subghz rx"), Cmd("scan", "subghz scan"), Cmd("selftest", "subghz selftest"),
        )),
        Group("INFRAROT", listOf(
            Cmd("rx", "ir rx"),
        )),
        Group("NFC / RFID", listOf(
            Cmd("read", "rfid read"), Cmd("info", "rfid info"),
            Cmd("emulate", "rfid emulate"), Cmd("erase", "rfid erase"),
        )),
        Group("WLAN", listOf(
            Cmd("wifi", "wifi"), Cmd("sniffer", "sniffer"), Cmd("listen", "listen"),
            Cmd("arp", "arp"), Cmd("webui", "webui"),
        )),
    )

    /** Echtes 'screen br <0-100>' (persistent). Stealth (off, nicht persistent) via Firmware-Handler. */
    val brightness = listOf(25, 50, 75, 100)

    val navUp = "nav up"
    val navDown = "nav down"
    val navLeft = "nav prev"
    val navRight = "nav next"
    val navSelect = "nav select"
    val navEsc = "nav esc"
}
