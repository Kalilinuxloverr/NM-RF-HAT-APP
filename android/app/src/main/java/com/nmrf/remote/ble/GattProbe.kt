package com.nmrf.remote.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GattChar(val uuid: String, val properties: List<String>)
data class GattService(val uuid: String, val chars: List<GattChar>)

/** Verbindet einmalig, liest die GATT-Tabelle, trennt wieder. Result statt Exception. */
class GattProbe(context: Context) {
    private val app = context.applicationContext
    private val adapter =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    suspend fun enumerate(address: String): Result<List<GattService>> =
        suspendCancellableCoroutine { cont ->
            val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            if (device == null) {
                cont.resume(Result.failure(IllegalStateException("kein Bluetooth-Adapter/Adresse")))
                return@suspendCancellableCoroutine
            }
            var gattRef: BluetoothGatt? = null
            var done = false
            fun finish(r: Result<List<GattService>>) {
                if (done) return
                done = true
                runCatching { gattRef?.disconnect() }
                runCatching { gattRef?.close() }
                if (cont.isActive) cont.resume(r)
            }
            val cb = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED ->
                            runCatching { g.discoverServices() }
                                .onFailure { finish(Result.failure(it)) }
                        BluetoothGatt.STATE_DISCONNECTED ->
                            finish(Result.failure(IllegalStateException("getrennt (status $status)")))
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    val services = g.services.map { s ->
                        GattService(
                            uuid = s.uuid.toString(),
                            chars = s.characteristics.map { c -> GattChar(c.uuid.toString(), propsOf(c)) },
                        )
                    }
                    finish(Result.success(services))
                }
            }
            gattRef = runCatching { device.connectGatt(app, false, cb) }
                .getOrElse { finish(Result.failure(it)); null }
            cont.invokeOnCancellation { finish(Result.failure(IllegalStateException("abgebrochen"))) }
        }

    private fun propsOf(c: BluetoothGattCharacteristic): List<String> {
        val p = c.properties
        val out = mutableListOf<String>()
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) out += "READ"
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) out += "WRITE"
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) out += "WRITE_NR"
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) out += "NOTIFY"
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) out += "INDICATE"
        return out
    }
}
