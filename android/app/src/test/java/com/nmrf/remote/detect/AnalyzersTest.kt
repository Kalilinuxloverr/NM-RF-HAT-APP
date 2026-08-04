package com.nmrf.remote.detect

import com.nmrf.remote.wifi.AccessPoint
import com.nmrf.remote.wifi.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzersTest {

    private fun ap(ssid: String, bssid: String, caps: String) =
        AccessPoint(ssid, bssid, 2412, -50, 1, Band.GHZ_2_4, 20, caps)

    // --- Rogue-AP ---------------------------------------------------------

    @Test fun openCloneOfSecuredIsAlert() {
        val aps = listOf(
            ap("Cafe", "00:11:22:33:44:01", "[WPA2-PSK-CCMP][ESS]"),
            ap("Cafe", "aa:bb:cc:dd:ee:02", "[ESS]"),   // offener Klon
        )
        val findings = RogueApAnalyzer.analyze(aps)
        assertTrue(findings.any { it.severity == Severity.ALERT && it.ssid == "Cafe" })
    }

    @Test fun singleApIsClean() {
        assertTrue(RogueApAnalyzer.analyze(listOf(ap("Home", "00:11:22:33:44:55", "[WPA2-PSK-CCMP][ESS]"))).isEmpty())
    }

    @Test fun capabilityAndMacHelpers() {
        assertTrue(RogueApAnalyzer.isOpen("[ESS]"))
        assertFalse(RogueApAnalyzer.isOpen("[WPA2-PSK-CCMP][ESS]"))
        assertTrue(RogueApAnalyzer.isLocallyAdministered("02:00:00:00:00:01"))
        assertFalse(RogueApAnalyzer.isLocallyAdministered("00:11:22:33:44:55"))
    }

    // --- Tracker ----------------------------------------------------------

    @Test fun tileSignatureIsFlagged() {
        val now = 1_000_000L
        val s = TrackSighting("11:22:33:44:55:66", "Tag", null, listOf("feed"), false, now - 5000, now, 3, -60, listOf(-60, -58))
        val f = TrackerAnalyzer.analyze(listOf(s), now).single()
        assertEquals(TrackerKind.TILE, f.kind)
        assertTrue(f.score >= 45)
    }

    @Test fun persistentUnknownIsFlaggedTransientIsNot() {
        val now = 1_000_000L
        val persistent = TrackSighting("aa", null, null, emptyList(), false, now - 200_000, now, 6, -70, emptyList())
        val transient = TrackSighting("bb", null, null, emptyList(), false, now - 1000, now, 1, -90, emptyList())
        val out = TrackerAnalyzer.analyze(listOf(persistent, transient), now)
        assertEquals(1, out.size)
        assertEquals(TrackerKind.PERSISTENT, out.single().kind)
    }

    // --- AD-Parser --------------------------------------------------------

    @Test fun parsesFlagsAndName() {
        // [len=2][type=01 Flags][06]  [len=3][type=09 Name]["AB"]
        val bytes = byteArrayOf(0x02, 0x01, 0x06, 0x03, 0x09, 0x41, 0x42)
        val s = BleAdParser.parse(bytes)
        assertEquals(2, s.size)
        assertEquals(0x01, s[0].type)
        assertEquals(0x09, s[1].type)
        assertEquals("AB", String(s[1].value))
    }

    @Test fun truncatedRecordDoesNotCrash() {
        // len behauptet 5 Byte, es folgt nur eines — darf nicht über das Array lesen
        val s = BleAdParser.parse(byteArrayOf(0x05, 0x09, 0x41))
        assertEquals(1, s.size)
        assertEquals("A", String(s[0].value))
    }

    @Test fun typeNameFallback() {
        assertEquals("Manufacturer Specific", BleAdParser.typeName(0xFF))
        assertNull(TrackerAnalyzer.analyze(emptyList(), 0L).firstOrNull())
    }
}
