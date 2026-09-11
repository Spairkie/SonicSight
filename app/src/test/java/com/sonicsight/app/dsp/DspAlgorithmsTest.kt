package com.sonicsight.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DspAlgorithmsTest {
    @Test
    fun rowShiftEstimator_recoversSubpixelMotion() {
        fun signal(n: Int, shift: Double) = FloatArray(n) { i ->
            val x = i - shift
            (0.65 * sin(2 * PI * x / 17.0) + 0.3 * sin(2 * PI * x / 7.0)).toFloat()
        }
        val estimate = RowShiftEstimator.estimate(signal(128, 0.0), signal(128, 0.72), 3)
        assertTrue(abs(abs(estimate) - 0.72f) < 0.25f)
    }

    @Test
    fun spectrumAnalyzer_findsKnownTone() {
        val sampleRate = 48_000
        val samples = FloatArray(sampleRate / 2) { i ->
            sin(2.0 * PI * 440.0 * i / sampleRate).toFloat()
        }
        val peak = SpectrumAnalyzer.dominantPeak(samples, sampleRate)!!
        assertEquals(440.0, peak.frequencyHz, 3.0)
        assertTrue(peak.peakToMedianDb > 20.0)
    }

    @Test
    fun nonUniformResampler_preservesTimeline() {
        val t = longArrayOf(0, 20_000, 40_000, 60_000, 300_000, 320_000, 340_000)
        val v = FloatArray(t.size) { i -> sin(2 * PI * 1000 * t[i] / 1e9).toFloat() }
        val out = NonUniformResampler.resample(v, t, maxRate = 48_000)
        assertTrue(out.sampleRate in 8_000..48_000)
        assertTrue(out.samples.size >= 2)
    }
}
