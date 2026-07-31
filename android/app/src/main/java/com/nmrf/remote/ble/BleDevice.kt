package com.nmrf.remote.ble

/** Ein aggregiertes BLE-Gerät (nach MAC zusammengefasst). */
data class BleDevice(
    val address: String,
    val name: String?,            // null = keiner beworben
    val rssi: Int,                // dBm
    val connectable: Boolean,
    val txPower: Int?,            // null = nicht im Advertisement
    val companyId: Int?,         // erste manufacturer-specific Company-ID
    val manufacturer: String?,   // aufgelöst via CompanyIds
    val serviceUuids: List<String>,
    val rawBytes: ByteArray,     // rohes Scan-Record
    val lastSeen: Long,
    val rssiHistory: List<Int>,  // letzte N RSSI-Werte
)
