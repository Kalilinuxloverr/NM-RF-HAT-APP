package com.nmrf.remote.wifi

/** Kleiner OUI-Auszug (MAC-Präfix -> Hersteller). Beispielhaft, keine vollständige DB. */
object Ouis {
    private val map = mapOf(
        "FC:FB:FB" to "Apple",
        "A4:83:E7" to "Apple",
        "3C:5A:B4" to "Google",
        "94:65:2D" to "OnePlus",
        "50:8F:4C" to "Xiaomi",
        "AC:DE:48" to "Espressif",
        "24:6F:28" to "Espressif",
        "F0:9F:C2" to "Ubiquiti",
        "B8:27:EB" to "Raspberry Pi",
        "DC:A6:32" to "Raspberry Pi",
        "00:1A:11" to "Google",
        "E4:5F:01" to "Raspberry Pi",
        "C0:25:E9" to "AVM (Fritz!Box)",
        "38:10:D5" to "AVM (Fritz!Box)",
        "1C:B7:2C" to "Huawei",
        "48:2C:A0" to "Samsung",
    )

    fun vendor(bssid: String): String? {
        if (bssid.length < 8) return null
        val prefix = bssid.uppercase().split(":").take(3).joinToString(":")
        return map[prefix]
    }
}
