package com.nmrf.remote.wifi

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioMathTest {

    @Test
    fun band_classification() {
        assertEquals(Band.GHZ_2_4, RadioMath.bandOf(2412))
        assertEquals(Band.GHZ_2_4, RadioMath.bandOf(2484))
        assertEquals(Band.GHZ_5, RadioMath.bandOf(5180))
        assertEquals(Band.GHZ_6, RadioMath.bandOf(5955))
        assertEquals(Band.UNKNOWN, RadioMath.bandOf(1000))
    }

    @Test
    fun channel_mapping() {
        assertEquals(1, RadioMath.channelOf(2412))
        assertEquals(6, RadioMath.channelOf(2437))
        assertEquals(13, RadioMath.channelOf(2472))
        assertEquals(14, RadioMath.channelOf(2484))   // Sonderfall Kanal 14
        assertEquals(36, RadioMath.channelOf(5180))
        assertEquals(149, RadioMath.channelOf(5745))
        assertEquals(1, RadioMath.channelOf(5955))     // 6 GHz Kanal 1
        assertEquals(0, RadioMath.channelOf(1000))     // unbekannt
    }
}
