package com.nmrf.remote.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FftTest {
    @Test fun peak_at_expected_bin() {
        val n = 256
        val bin = 16
        val s = FloatArray(n) { sin(2.0 * PI * bin * it / n).toFloat() }
        val mag = Fft.magnitudes(s)
        val peak = mag.indices.maxByOrNull { mag[it] } ?: -1
        assertTrue("peak=$peak erwartet~$bin", kotlin.math.abs(peak - bin) <= 1)
    }
}
