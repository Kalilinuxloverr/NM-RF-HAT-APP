package com.nmrf.remote.wifi

import android.net.wifi.ScanResult
import android.os.Build

data class AccessPoint(
    val ssid: String,        // leer = hidden
    val bssid: String,
    val freqMhz: Int,
    val rssi: Int,           // dBm, negativ
    val channel: Int,
    val band: Band,
    val widthMhz: Int,       // 20/40/80/160
    val capabilities: String = "",
) {
    companion object {
        fun fromScan(r: ScanResult): AccessPoint {
            val name = if (Build.VERSION.SDK_INT >= 33) {
                r.wifiSsid?.toString()?.trim('"') ?: ""
            } else {
                @Suppress("DEPRECATION") (r.SSID ?: "")
            }
            val width = when (r.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ,
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                else -> 20
            }
            return AccessPoint(
                ssid = name,
                bssid = r.BSSID ?: "",
                freqMhz = r.frequency,
                rssi = r.level,
                channel = RadioMath.channelOf(r.frequency),
                band = RadioMath.bandOf(r.frequency),
                widthMhz = width,
                capabilities = r.capabilities ?: "",
            )
        }
    }
}
