package com.nmrf.remote.wifi

enum class Band { GHZ_2_4, GHZ_5, GHZ_6, UNKNOWN }

/** Pure Frequenz-Mathematik: MHz -> Band / Kanal. Testbar ohne Android. */
object RadioMath {
    fun bandOf(mhz: Int): Band = when (mhz) {
        in 2400..2499 -> Band.GHZ_2_4
        in 4900..5899 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        else -> Band.UNKNOWN
    }

    /** 0 = unbekannt. */
    fun channelOf(mhz: Int): Int = when (bandOf(mhz)) {
        Band.GHZ_2_4 -> if (mhz == 2484) 14 else (mhz - 2407) / 5
        Band.GHZ_5 -> (mhz - 5000) / 5
        Band.GHZ_6 -> (mhz - 5950) / 5
        Band.UNKNOWN -> 0
    }
}
