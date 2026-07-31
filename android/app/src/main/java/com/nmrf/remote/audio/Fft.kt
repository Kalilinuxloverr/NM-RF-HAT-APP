package com.nmrf.remote.audio

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.PI

/** Pure iterative Radix-2 Cooley-Tukey FFT (Größe = Zweierpotenz). Testbar ohne Android. */
object Fft {
    fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = kotlin.math.sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curR = 1f; var curI = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val reIdx = i + k
                    val reIdx2 = i + k + half
                    val bR = re[reIdx2] * curR - im[reIdx2] * curI
                    val bI = re[reIdx2] * curI + im[reIdx2] * curR
                    val aR = re[reIdx]; val aI = im[reIdx]
                    re[reIdx] = aR + bR; im[reIdx] = aI + bI
                    re[reIdx2] = aR - bR; im[reIdx2] = aI - bI
                    val nCurR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = nCurR
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Hann-Fenster + Magnitude der ersten n/2 Bins. */
    fun magnitudes(samples: FloatArray): FloatArray {
        val n = samples.size
        val re = FloatArray(n)
        val im = FloatArray(n)
        for (i in 0 until n) {
            val w = 0.5f * (1f - cos(2.0 * PI * i / (n - 1)).toFloat())
            re[i] = samples[i] * w
        }
        transform(re, im)
        val out = FloatArray(n / 2)
        for (i in 0 until n / 2) out[i] = hypot(re[i].toDouble(), im[i].toDouble()).toFloat()
        return out
    }
}
