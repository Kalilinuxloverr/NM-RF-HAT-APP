package com.nmrf.remote.hat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.UUID

private val NUS_SVC = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
private val NUS_RX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
private val NUS_TX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

@Suppress("DEPRECATION")
class BleBruceLink(context: Context) : BruceLink {
    private val app = context.applicationContext
    private val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    override val state: StateFlow<LinkState> = _state.asStateFlow()
    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 512)
    override val lines: Flow<String> = _lines.asSharedFlow()
    private val _packets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 512)
    override val packets: Flow<ByteArray> = _packets.asSharedFlow()

    private val assembler = LineAssembler()
    private var pending = ByteArray(0)   // Demux-Puffer (Binär vs. Text)
    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private val queue = ArrayDeque<ByteArray>()
    private var writing = false

    @SuppressLint("MissingPermission")
    override fun connect(address: String, autoConnect: Boolean) {
        if (_state.value != LinkState.DISCONNECTED) return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        _state.value = LinkState.CONNECTING
        gatt = device.connectGatt(app, autoConnect, cb)
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        cleanup()
    }

    override fun send(command: String) {
        val chunks = Chunker.chunks(command, mtu)
        synchronized(queue) { chunks.forEach { queue.add(it) } }
        pump()
    }

    private fun cleanup() {
        gatt = null; rxChar = null
        synchronized(queue) { queue.clear() }
        writing = false; pending = ByteArray(0)
        _state.value = LinkState.DISCONNECTED
    }

    /** Trennt Binär-Draw-Pakete (0xAA + Längenbyte) von ASCII-Text (nie 0xAA). */
    private fun demux(data: ByteArray) {
        pending += data
        var off = 0
        while (off < pending.size) {
            val b = pending[off].toInt() and 0xFF
            if (b == 0xAA) {
                if (pending.size - off < 2) break
                val size = pending[off + 1].toInt() and 0xFF
                if (size < 3) { off++; continue }   // Robustheit gegen Müll
                if (pending.size - off < size) break
                _packets.tryEmit(pending.copyOfRange(off, off + size))
                off += size
            } else {
                var end = off
                while (end < pending.size && (pending[end].toInt() and 0xFF) != 0xAA) end++
                assembler.feed(pending.copyOfRange(off, end)).forEach { _lines.tryEmit(it) }
                off = end
            }
        }
        pending = if (off >= pending.size) ByteArray(0) else pending.copyOfRange(off, pending.size)
    }

    @SuppressLint("MissingPermission")
    private fun pump() {
        val g = gatt ?: return
        val rx = rxChar ?: return
        val next: ByteArray? = synchronized(queue) { if (writing || queue.isEmpty()) null else queue.poll().also { writing = true } }
        if (next == null) return
        rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        rx.value = next
        g.writeCharacteristic(rx)
    }

    private val cb = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.requestMtu(247)
                BluetoothProfile.STATE_DISCONNECTED -> { runCatching { g.close() }; cleanup() }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, m: Int, status: Int) { mtu = m; g.discoverServices() }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(NUS_SVC)
            val rx = svc?.getCharacteristic(NUS_RX)
            val tx = svc?.getCharacteristic(NUS_TX)
            if (rx == null || tx == null) { disconnect(); return }
            rxChar = rx
            g.setCharacteristicNotification(tx, true)
            val desc = tx.getDescriptor(CCCD)
            if (desc != null) {
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(desc)
            } else {
                _state.value = LinkState.CONNECTED
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            _state.value = LinkState.CONNECTED
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid == NUS_TX) ch.value?.let { demux(it) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            synchronized(queue) { writing = false }
            pump()
        }
    }
}
