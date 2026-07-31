package com.nmrf.remote.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Naht für Tests: der ViewModel-Test injiziert einen Fake. */
interface BleSource {
    val devices: Flow<List<BleDevice>>
    fun isReady(): Boolean
}

class BleScanner(context: Context) : BleSource {
    private val app = context.applicationContext
    private val adapter =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override fun isReady(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    override val devices: Flow<List<BleDevice>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val found = LinkedHashMap<String, BleDevice>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val prev = found[result.device.address]?.rssiHistory ?: emptyList()
                val dev = result.toBleDevice(prev)
                found[dev.address] = dev
                trySend(found.values.toList())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { r ->
                    val prev = found[r.device.address]?.rssiHistory ?: emptyList()
                    val dev = r.toBleDevice(prev)
                    found[dev.address] = dev
                }
                trySend(found.values.toList())
            }

            override fun onScanFailed(errorCode: Int) { /* ponytail: still, UI zeigt "keine Geräte" */ }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, cb)
        } catch (e: SecurityException) {
            trySend(emptyList())
        }
        awaitClose { runCatching { scanner.stopScan(cb) } }
    }
}

@SuppressLint("MissingPermission")
private fun ScanResult.toBleDevice(prevHistory: List<Int>): BleDevice {
    val rec = scanRecord
    val msd = rec?.manufacturerSpecificData
    val companyId: Int? = if (msd != null && msd.size() > 0) msd.keyAt(0) else null
    val advName = runCatching { device.name }.getOrNull() ?: rec?.deviceName
    val uuids = rec?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
    val tx = rec?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
    val history = (prevHistory + rssi).takeLast(40)
    return BleDevice(
        address = device.address,
        name = advName?.ifBlank { null },
        rssi = rssi,
        connectable = isConnectable,
        txPower = tx,
        companyId = companyId,
        manufacturer = CompanyIds.name(companyId),
        serviceUuids = uuids,
        rawBytes = rec?.bytes ?: ByteArray(0),
        lastSeen = System.currentTimeMillis(),
        rssiHistory = history,
    )
}
