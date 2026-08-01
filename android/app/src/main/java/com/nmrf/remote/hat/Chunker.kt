package com.nmrf.remote.hat

/** Zerlegt einen Befehl (+\n) in MTU-gerechte Häppchen (pure, testbar). */
object Chunker {
    fun chunks(command: String, mtu: Int): List<ByteArray> {
        val payload = (command + "\n").toByteArray(Charsets.UTF_8)
        val size = (mtu - 3).coerceAtLeast(1)   // ATT-Overhead
        if (payload.size <= size) return listOf(payload)
        val out = ArrayList<ByteArray>()
        var i = 0
        while (i < payload.size) {
            val end = minOf(i + size, payload.size)
            out.add(payload.copyOfRange(i, end))
            i = end
        }
        return out
    }
}
