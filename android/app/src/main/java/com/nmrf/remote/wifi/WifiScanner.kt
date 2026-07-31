package com.nmrf.remote.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Naht fuer Tests: der ViewModel-Test injiziert einen Fake. */
interface WifiSource {
    val results: Flow<List<AccessPoint>>
    fun requestScan(): Boolean
    fun latest(): List<AccessPoint>
}

class WifiScanner(context: Context) : WifiSource {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("MissingPermission")
    override fun latest(): List<AccessPoint> =
        runCatching { wifi.scanResults.map(AccessPoint::fromScan) }.getOrDefault(emptyList())

    // startScan() ist deprecated, bleibt aber die einzige AOSP-Trigger-API. false = gedrosselt/abgelehnt.
    @Suppress("DEPRECATION")
    override fun requestScan(): Boolean =
        runCatching { wifi.startScan() }.getOrDefault(false)

    override val results: Flow<List<AccessPoint>> = callbackFlow {
        trySend(latest())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { trySend(latest()) }
        }
        app.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        awaitClose { app.unregisterReceiver(receiver) }
    }
}
