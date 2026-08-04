package com.nmrf.remote.detect

import com.nmrf.remote.wifi.AccessPoint
import com.nmrf.remote.wifi.Band

/** Gemeinsame Schweregrade für alle Detektoren. */
enum class Severity { INFO, WARN, ALERT }

// ---------------------------------------------------------------------------
// Rogue-AP / Evil-Twin
// ---------------------------------------------------------------------------

data class RogueFinding(
    val ssid: String,
    val severity: Severity,
    val title: String,
    val detail: String,
    val bssids: List<String>,
)

/**
 * Findet WLAN-Auffälligkeiten aus einem Scan-Snapshot — rein defensiv:
 *  - offener Klon eines gesicherten Netzes (klassischer Evil-Twin-Köder)  → ALERT
 *  - gleiche SSID auf mehreren BSSIDs (Klon ODER legitimes Mesh)           → WARN
 *  - lokal verwaltete / zufällige BSSID (typisch für Karma/Rogue-Tools)    → WARN
 */
object RogueApAnalyzer {

    fun analyze(aps: List<AccessPoint>): List<RogueFinding> {
        val out = ArrayList<RogueFinding>()
        val named = aps.filter { it.ssid.isNotBlank() }

        named.groupBy { it.ssid }.forEach { (ssid, group) ->
            val bssids = group.map { it.bssid }.distinct()
            if (bssids.size < 2) return@forEach
            val secured = group.any { !isOpen(it.capabilities) }
            val open = group.any { isOpen(it.capabilities) }
            if (secured && open) {
                out += RogueFinding(
                    ssid, Severity.ALERT, "Offener Klon eines gesicherten Netzes",
                    "„$ssid“ existiert gesichert UND offen parallel — typischer Evil-Twin-Köder. Nicht verbinden.",
                    bssids,
                )
            } else {
                out += RogueFinding(
                    ssid, Severity.WARN, "SSID auf ${bssids.size} BSSIDs",
                    "Mehrere Funkzellen mit gleichem Namen. Kann Mesh/Roaming sein — oder ein Klon. BSSIDs prüfen.",
                    bssids,
                )
            }
        }

        named.filter { isLocallyAdministered(it.bssid) }
            .groupBy { it.ssid }
            .forEach { (ssid, group) ->
                out += RogueFinding(
                    ssid, Severity.WARN, "Zufalls-/lokale MAC",
                    "BSSID ist lokal verwaltet (zufällig gesetzt) — ungewöhnlich für echte APs, häufig bei Rogue-Tools.",
                    group.map { it.bssid }.distinct(),
                )
            }

        return out.sortedByDescending { it.severity.ordinal }
    }

    /** Offen = keine Verschlüsselung im capabilities-String. */
    fun isOpen(caps: String): Boolean {
        val c = caps.uppercase()
        return !listOf("WPA", "RSN", "WEP", "PSK", "SAE", "EAP").any { c.contains(it) }
    }

    /** Bit 0x02 des ersten Oktetts = lokal verwaltete (nicht herstellervergebene) MAC. */
    fun isLocallyAdministered(bssid: String): Boolean {
        val first = bssid.substringBefore(':').ifBlank { return false }
        val b = first.toIntOrNull(16) ?: return false
        return (b and 0x02) != 0
    }
}

// ---------------------------------------------------------------------------
// Kanal-Belegung (Spektrum-Screen)
// ---------------------------------------------------------------------------

data class ChannelStat(val channel: Int, val count: Int, val maxRssi: Int)

object SpectrumAnalyzer {
    /** Pro Kanal: Anzahl APs + stärkstes Signal. Aufsteigend nach Kanal. */
    fun channelLoad(aps: List<AccessPoint>, band: Band): List<ChannelStat> =
        aps.filter { it.band == band }
            .groupBy { it.channel }
            .map { (ch, g) -> ChannelStat(ch, g.size, g.maxOf { it.rssi }) }
            .sortedBy { it.channel }
}

// ---------------------------------------------------------------------------
// Tracker / Follower (BLE über Zeit)
// ---------------------------------------------------------------------------

data class TrackSighting(
    val address: String,
    val name: String?,
    val companyId: Int?,
    val serviceUuids: List<String>,
    val connectable: Boolean,
    val firstSeen: Long,
    val lastSeen: Long,
    val count: Int,
    val rssiAvg: Int,
    val rssiHistory: List<Int>,
)

enum class TrackerKind { APPLE_FINDMY, TILE, SAMSUNG, PERSISTENT }

data class TrackerFinding(
    val sighting: TrackSighting,
    val kind: TrackerKind,
    val score: Int,       // 0..100
    val reason: String,
)

/**
 * Anti-Stalking: markiert BLE-Geräte, die dir über Zeit „folgen“.
 * Bekannte Tag-Signaturen (Apple Find My / Tile / Samsung) + Persistenz-Heuristik.
 * Bewusst als Verdacht gekennzeichnet — nicht jeder Treffer ist ein Verfolger.
 */
object TrackerAnalyzer {

    private const val APPLE = 0x004C
    private const val SAMSUNG_CID = 0x0075

    fun analyze(sightings: List<TrackSighting>, now: Long, minSpanMs: Long = 120_000L): List<TrackerFinding> {
        val out = ArrayList<TrackerFinding>()
        for (s in sightings) {
            val span = s.lastSeen - s.firstSeen
            val persistent = span >= minSpanMs && s.count >= 4
            val near = s.rssiAvg >= -75         // grob in Reichweite geblieben

            val kind = when {
                has16(s.serviceUuids, "feed") || has16(s.serviceUuids, "feec") -> TrackerKind.TILE
                s.companyId == APPLE && !s.connectable -> TrackerKind.APPLE_FINDMY
                s.companyId == SAMSUNG_CID && !s.connectable -> TrackerKind.SAMSUNG
                persistent -> TrackerKind.PERSISTENT
                else -> null
            } ?: continue

            var score = 0
            if (kind != TrackerKind.PERSISTENT) score += 45          // bekannte Tag-Signatur
            if (persistent) score += 35
            if (near) score += 20
            score = score.coerceAtMost(100)

            val reason = buildString {
                when (kind) {
                    TrackerKind.TILE -> append("Tile-Tag-Signatur")
                    TrackerKind.APPLE_FINDMY -> append("Apple Find My (AirTag?)")
                    TrackerKind.SAMSUNG -> append("Samsung SmartTag-Signatur")
                    TrackerKind.PERSISTENT -> append("Unbekanntes Gerät folgt anhaltend")
                }
                if (persistent) append(" · seit ${span / 60_000} min, ${s.count}× gesehen")
                if (near) append(" · in Reichweite")
            }
            out += TrackerFinding(s, kind, score, reason)
        }
        return out.sortedByDescending { it.score }
    }

    /** Enthält die UUID-Liste eine bestimmte 16-bit-UUID (kurz oder 128-bit-Form)? */
    fun has16(uuids: List<String>, id16: String): Boolean {
        val id = id16.lowercase()
        return uuids.any { u -> val s = u.lowercase(); s == id || s.startsWith("0000$id") }
    }
}

// ---------------------------------------------------------------------------
// BLE-Advertisement-Parser (LTV-Struktur)
// ---------------------------------------------------------------------------

data class AdStructure(val type: Int, val typeName: String, val value: ByteArray, val hex: String)

/** Zerlegt ein rohes BLE-Advertisement in seine AD-Strukturen [len][type][value]. */
object BleAdParser {

    fun parse(bytes: ByteArray): List<AdStructure> {
        val out = ArrayList<AdStructure>()
        var i = 0
        while (i < bytes.size) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0) break                        // Padding / Ende
            if (i + 1 >= bytes.size) break             // kein Typ-Byte mehr
            val type = bytes[i + 1].toInt() and 0xFF
            val valStart = i + 2
            val valEnd = (i + 1 + len).coerceAtMost(bytes.size)
            val value = if (valStart <= valEnd) bytes.copyOfRange(valStart, valEnd) else ByteArray(0)
            out += AdStructure(type, typeName(type), value, value.toHex())
            i += len + 1
        }
        return out
    }

    fun typeName(type: Int): String = when (type) {
        0x01 -> "Flags"
        0x02, 0x03 -> "16-bit Service-UUIDs"
        0x04, 0x05 -> "32-bit Service-UUIDs"
        0x06, 0x07 -> "128-bit Service-UUIDs"
        0x08 -> "Short Local Name"
        0x09 -> "Complete Local Name"
        0x0A -> "TX Power Level"
        0x16 -> "Service Data (16-bit)"
        0x19 -> "Appearance"
        0x1B -> "LE Bluetooth Device Address"
        0x20, 0x21 -> "Service Data (32/128-bit)"
        0xFF -> "Manufacturer Specific"
        else -> "Typ 0x%02X".format(type)
    }

    private fun ByteArray.toHex(): String =
        if (isEmpty()) "" else joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
