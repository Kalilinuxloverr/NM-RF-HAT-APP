package com.nmrf.remote.hat

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** WLAN-Mirror über das CYD-eigene AP (NMRF-HAT): /getscreen liefert dieselben 0xAA-Draw-Ops. */
class WifiMirror(context: Context) {
    private val app = context.applicationContext
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var cb: ConnectivityManager.NetworkCallback? = null
    private var net: Network? = null
    private var cookie: String? = null
    private val base = "http://172.0.0.1"

    suspend fun start(): Boolean {
        if (!joinAp()) return false
        return withContext(Dispatchers.IO) { login() }
    }

    private suspend fun joinAp(): Boolean = suspendCancellableCoroutine { cont ->
        val spec = WifiNetworkSpecifier.Builder().setSsid("NMRF-HAT").setWpa2Passphrase("nmrflab1").build()
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec).build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                net = n; cm.bindProcessToNetwork(n)
                if (cont.isActive) cont.resume(true)
            }
            override fun onUnavailable() { if (cont.isActive) cont.resume(false) }
        }
        cb = callback
        runCatching { cm.requestNetwork(req, callback) }.onFailure { if (cont.isActive) cont.resume(false) }
        cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(callback) } }
    }

    private fun login(): Boolean = runCatching {
        val c = URL("$base/login").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 4000; c.readTimeout = 4000
        c.outputStream.use { it.write("username=admin&password=bruce".toByteArray()) }
        runCatching { c.inputStream.use { it.readBytes() } }
        cookie = c.headerFields["Set-Cookie"]?.firstOrNull { it.contains("BRUCESESSION") }?.substringBefore(";")
        c.disconnect()
        cookie != null
    }.getOrDefault(false)

    fun packets(intervalMs: Long = 250): Flow<ByteArray> = flow {
        val split = PacketSplitter()
        while (true) {
            runCatching { getScreen() }.getOrNull()?.let { split.feed(it).forEach { p -> emit(p) } }
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun getScreen(): ByteArray? {
        val c = URL("$base/getscreen").openConnection() as HttpURLConnection
        cookie?.let { c.setRequestProperty("Cookie", it) }
        c.connectTimeout = 3000; c.readTimeout = 3000
        return try { if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null } finally { c.disconnect() }
    }

    fun stop() {
        runCatching { cb?.let { cm.unregisterNetworkCallback(it) } }
        runCatching { cm.bindProcessToNetwork(null) }
        cb = null; net = null; cookie = null
    }
}

private class PacketSplitter {
    private var pending = ByteArray(0)
    fun feed(data: ByteArray): List<ByteArray> {
        pending += data
        val out = ArrayList<ByteArray>()
        var off = 0
        while (off < pending.size) {
            if ((pending[off].toInt() and 0xFF) != 0xAA) { off++; continue }
            if (pending.size - off < 2) break
            val size = pending[off + 1].toInt() and 0xFF
            if (size < 3) { off++; continue }
            if (pending.size - off < size) break
            out.add(pending.copyOfRange(off, off + size)); off += size
        }
        pending = if (off >= pending.size) ByteArray(0) else pending.copyOfRange(off, pending.size)
        return out
    }
}
