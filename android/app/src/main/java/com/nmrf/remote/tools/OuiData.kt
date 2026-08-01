package com.nmrf.remote.tools

/** Kombinierte Offline-DB: WLAN-OUI (MAC-Präfix) + BLE-Company-ID -> Hersteller. Auszug. */
object OuiData {
    data class Row(val kind: String, val key: String, val vendor: String)

    val rows = listOf(
        Row("OUI", "FC:FB:FB", "Apple"), Row("OUI", "A4:83:E7", "Apple"), Row("OUI", "3C:5A:B4", "Google"),
        Row("OUI", "94:65:2D", "OnePlus"), Row("OUI", "50:8F:4C", "Xiaomi"), Row("OUI", "AC:DE:48", "Espressif"),
        Row("OUI", "24:6F:28", "Espressif"), Row("OUI", "F0:9F:C2", "Ubiquiti"), Row("OUI", "B8:27:EB", "Raspberry Pi"),
        Row("OUI", "DC:A6:32", "Raspberry Pi"), Row("OUI", "C0:25:E9", "AVM (Fritz!Box)"), Row("OUI", "38:10:D5", "AVM (Fritz!Box)"),
        Row("OUI", "1C:B7:2C", "Huawei"), Row("OUI", "48:2C:A0", "Samsung"), Row("OUI", "00:1A:11", "Google"),
        Row("BLE-CID", "0x004C", "Apple"), Row("BLE-CID", "0x0006", "Microsoft"), Row("BLE-CID", "0x00E0", "Google"),
        Row("BLE-CID", "0x0075", "Samsung"), Row("BLE-CID", "0x0059", "Nordic Semi"), Row("BLE-CID", "0x0157", "Huawei"),
        Row("BLE-CID", "0x0117", "Espressif"), Row("BLE-CID", "0x00D2", "Fitbit"), Row("BLE-CID", "0x0087", "Garmin"),
        Row("BLE-CID", "0x0171", "Amazon"), Row("BLE-CID", "0x038F", "Xiaomi"), Row("BLE-CID", "0x000F", "Broadcom"),
    )
}
