package com.sonicsight.app.dsp

import kotlin.math.abs
import kotlin.math.max

object SignalConditioner {
    /** Remove DC and a slow moving baseline, then normalize to [-0.98, 0.98]. */
    fun condition(input: FloatArray, sampleRate: Int): FloatArray {
        if (input.isEmpty()) return input
        val centered = input.copyOf()
        val mean = centered.average().toFloat()
        for (i in centered.indices) centered[i] -= mean

        // First-order high-pass. 60 Hz is a reasonable initial speech/vibration cutoff.
        val cutoff = 60.0
        val rc = 1.0 / (2.0 * Math.PI * cutoff)
        val dt = 1.0 / sampleRate.coerceAtLeast(1)
        val alpha = (rc / (rc + dt)).toFloat()
        var yPrev = 0f
        var xPrev = centered[0]
        for (i in centered.indices) {
            val x = centered[i]
            val y = alpha * (yPrev + x - xPrev)
            centered[i] = y
            yPrev = y
            xPrev = x
        }

        var peak = 1e-9f
        for (x in centered) peak = max(peak, abs(x))
        val gain = 0.98f / peak
        for (i in centered.indices) centered[i] *= gain
        return centered
    }
}
