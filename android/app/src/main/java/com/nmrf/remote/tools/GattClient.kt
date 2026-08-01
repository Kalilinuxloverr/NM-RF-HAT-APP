package com.nmrf.remote.tools

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

data class GattChr(val uuid: String, val props: List<String>, val read: Boolean, val write: Boolean, val notify: Boolean)
data class GattSvc(val uuid: String, val chars: List<GattChr>)

@Suppress("DEPRECATION")
class GattClient(context: Context) {
    private val app = context.applicationContext
    private val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var gatt: BluetoothGatt? = null
    private val charMap = HashMap<String, BluetoothGattCharacteristic>()

    private val _state = MutableStateFlow("disconnected")
    val state: StateFlow<String> = _state.asStateFlow()
    private val _services = MutableStateFlow<List<GattSvc>>(emptyList())
    val services: StateFlow<List<GattSvc>> = _services.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()
    private fun logln(s: String) { _log.value = (_log.value + s).takeLast(200) }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val d = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        _state.value = "connecting"; logln("· connect $address")
        gatt = d.connectGatt(app, false, cb)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching { gatt?.disconnect() }; runCatching { gatt?.close() }
        gatt = null; charMap.clear(); _services.value = emptyList(); _state.value = "disconnected"
    }

    @SuppressLint("MissingPermission")
    fun read(uuid: String) { charMap[uuid]?.let { gatt?.readCharacteristic(it) } }

    @SuppressLint("MissingPermission")
    fun write(uuid: String, hex: String) {
        val c = charMap[uuid] ?: return
        val b = hexToBytes(hex) ?: run { logln("· ungültiger Hex"); return }
        c.value = b; c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt?.writeCharacteristic(c); logln("W ${short(uuid)} <- ${b.toHex()}")
    }

    @SuppressLint("MissingPermission")
    fun setNotify(uuid: String, enable: Boolean) {
        val c = charMap[uuid] ?: return
        gatt?.setCharacteristicNotification(c, enable)
        c.getDescriptor(CCCD)?.let {
            it.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(it)
        }
        logln("${if (enable) "N+" else "N-"} ${short(uuid)}")
    }

    private val cb = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.requestMtu(247)
                BluetoothProfile.STATE_DISCONNECTED -> { runCatching { g.close() }; disconnect() }
            }
        }
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, m: Int, s: Int) { g.discoverServices() }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            charMap.clear()
            _services.value = g.services.map { s ->
                GattSvc(s.uuid.toString(), s.characteristics.map { c ->
                    charMap[c.uuid.toString()] = c
                    val p = c.properties
                    GattChr(
                        c.uuid.toString(), propsOf(p),
                        p and BluetoothGattCharacteristic.PROPERTY_READ != 0,
                        (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) || (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0),
                        (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) || (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0),
                    )
                })
            }
            _state.value = "connected"; logln("· ${_services.value.size} Services")
        }
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            logln("R ${short(c.uuid.toString())} = ${(c.value ?: ByteArray(0)).toHex()}")
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            logln("N ${short(c.uuid.toString())} = ${(c.value ?: ByteArray(0)).toHex()}")
        }
    }
}

private fun propsOf(p: Int): List<String> {
    val o = mutableListOf<String>()
    if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) o += "R"
    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) o += "W"
    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) o += "Wnr"
    if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) o += "N"
    if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) o += "I"
    return o
}
private fun short(u: String) = if (u.length >= 8) u.substring(4, 8) else u
private fun ByteArray.toHex() = if (isEmpty()) "(leer)" else joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
private fun hexToBytes(s: String): ByteArray? {
    val h = s.replace(" ", "").replace("0x", "")
    if (h.isEmpty() || h.length % 2 != 0) return null
    return runCatching { ByteArray(h.length / 2) { ((h.substring(it * 2, it * 2 + 2)).toInt(16)).toByte() } }.getOrNull()
}
