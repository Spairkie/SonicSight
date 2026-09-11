package com.sonicsight.app.dsp

import kotlin.math.roundToInt

/**
 * Converts monotonically increasing, irregularly timed samples to a regular PCM timebase.
 * Linear interpolation is intentionally simple for the MVP; the sample timestamps preserve
 * rolling-shutter row timing and explicit inter-frame gaps instead of pretending rows are
 * uniformly adjacent across frame boundaries.
 */
object NonUniformResampler {
    data class Result(val samples: FloatArray, val sampleRate: Int)

    fun resample(
        values: FloatArray,
        timesNs: LongArray,
        preferredRate: Int? = null,
        maxRate: Int = 48_000
    ): Result {
        require(values.size == timesNs.size) { "values/timestamps size mismatch" }
        if (values.size < 2) return Result(values.copyOf(), preferredRate ?: 8_000)

        val positiveDeltas = ArrayList<Long>(values.size)
        for (i in 1 until timesNs.size) {
            val d = timesNs[i] - timesNs[i - 1]
            if (d > 0L) positiveDeltas += d
        }
        require(positiveDeltas.isNotEmpty()) { "timestamps must increase" }
        positiveDeltas.sort()
        val medianDelta = positiveDeltas[positiveDeltas.size / 2].coerceAtLeast(1L)
        val inferredRate = (1_000_000_000.0 / medianDelta.toDouble()).roundToInt()
        val rate = (preferredRate ?: inferredRate).coerceIn(8_000, maxRate)

        val start = timesNs.first()
        val end = timesNs.last()
        val durationNs = (end - start).coerceAtLeast(1L)
        val count = ((durationNs / 1_000_000_000.0) * rate).roundToInt().coerceAtLeast(2)
        val out = FloatArray(count)
        val stepNs = 1_000_000_000.0 / rate.toDouble()

        var right = 1
        for (i in out.indices) {
            val t = start + (i * stepNs).toLong()
            while (right < timesNs.lastIndex && timesNs[right] < t) right++
            val left = (right - 1).coerceAtLeast(0)
            val t0 = timesNs[left]
            val t1 = timesNs[right]
            if (t1 <= t0) {
                out[i] = values[right]
            } else {
                val a = ((t - t0).toDouble() / (t1 - t0).toDouble()).coerceIn(0.0, 1.0)
                out[i] = (values[left] * (1.0 - a) + values[right] * a).toFloat()
            }
        }
        return Result(out, rate)
    }
}
