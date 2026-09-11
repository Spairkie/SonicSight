package com.sonicsight.app.dsp

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Estimates sub-pixel horizontal displacement between two luma scanlines.
 * It uses zero-mean normalized correlation at integer shifts followed by
 * a three-point parabolic interpolation around the best peak.
 *
 * This is intentionally simple enough for the first Android prototype.
 * The research-grade path can later replace it with complex phase/wavelet analysis.
 */
object RowShiftEstimator {
    fun estimate(reference: FloatArray, current: FloatArray, maxShift: Int = 3): Float {
        require(reference.size == current.size)
        require(reference.size > maxShift * 2 + 8)

        val scores = FloatArray(maxShift * 2 + 1)
        for (shift in -maxShift..maxShift) {
            scores[shift + maxShift] = zncc(reference, current, shift)
        }
        var best = 0
        for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
        if (best == 0 || best == scores.lastIndex) return (best - maxShift).toFloat()

        val ym = scores[best - 1]
        val y0 = scores[best]
        val yp = scores[best + 1]
        val denom = ym - 2f * y0 + yp
        val delta = if (kotlin.math.abs(denom) < 1e-6f) 0f else 0.5f * (ym - yp) / denom
        return (best - maxShift) + delta.coerceIn(-1f, 1f)
    }

    private fun zncc(a: FloatArray, b: FloatArray, shift: Int): Float {
        val start = max(0, -shift)
        val end = minOf(a.size, b.size - shift)
        if (end - start < 8) return -1f
        var ma = 0.0
        var mb = 0.0
        var n = 0
        for (i in start until end) {
            ma += a[i]
            mb += b[i + shift]
            n++
        }
        ma /= n
        mb /= n
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in start until end) {
            val xa = a[i] - ma
            val xb = b[i + shift] - mb
            num += xa * xb
            da += xa * xa
            db += xb * xb
        }
        val den = sqrt(da * db)
        return if (den < 1e-12) -1f else (num / den).toFloat()
    }
}
