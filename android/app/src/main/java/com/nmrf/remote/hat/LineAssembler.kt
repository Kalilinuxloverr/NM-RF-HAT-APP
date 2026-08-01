package com.nmrf.remote.hat

/** Fügt fragmentierte BLE-Notify-Bytes zu vollständigen Zeilen zusammen (pure, testbar). */
class LineAssembler {
    private val buf = StringBuilder()

    fun feed(bytes: ByteArray): List<String> {
        buf.append(String(bytes, Charsets.UTF_8))
        val out = mutableListOf<String>()
        var nl = buf.indexOf("\n")
        while (nl >= 0) {
            out.add(buf.substring(0, nl).trimEnd('\r'))
            buf.delete(0, nl + 1)
            nl = buf.indexOf("\n")
        }
        return out
    }
}
