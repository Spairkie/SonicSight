package com.sonicsight.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small dependency-free FFT helper used to turn an extracted vibration waveform into
 * objective device-test measurements. It intentionally reports a spectral peak rather
 * than claiming that the recovered waveform is intelligible speech.
 */
object SpectrumAnalyzer {
    data class Peak(
        val frequencyHz: Double,
        val magnitude: Double,
        val peakToMedianDb: Double,
        val fftSize: Int
    )

    fun dominantPeak(
        samples: FloatArray,
        sampleRate: Int,
        minHz: Double = 50.0,
        maxHz: Double = min(5_000.0, sampleRate * 0.45)
    ): Peak? {
        if (samples.size < 128 || sampleRate <= 0 || maxHz <= minHz) return null

        val n = highestPowerOfTwoAtMost(min(samples.size, 16_384))
        if (n < 128) return null
        val start = ((samples.size - n) / 2).coerceAtLeast(0)
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        for (i in 0 until n) {
            val window = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1).coerceAtLeast(1))
            real[i] = samples[start + i] * window
        }
        fftInPlace(real, imag)

        val first = max(1, (minHz * n / sampleRate).toInt())
        val last = min(n / 2 - 1, (maxHz * n / sampleRate).toInt())
        if (last <= first) return null

        val magnitudes = DoubleArray(last - first + 1)
        var peakIndex = first
        var peakMagnitude = 0.0
        for (bin in first..last) {
            val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
            magnitudes[bin - first] = mag
            if (mag > peakMagnitude) {
                peakMagnitude = mag
                peakIndex = bin
            }
        }

        val sorted = magnitudes.copyOf().also { it.sort() }
        val median = sorted[sorted.size / 2].coerceAtLeast(1e-12)
        val db = 20.0 * ln((peakMagnitude / median).coerceAtLeast(1e-12)) / ln(10.0)

        val delta = if (peakIndex > first && peakIndex < last) {
            val ym = magnitude(real, imag, peakIndex - 1)
            val y0 = magnitude(real, imag, peakIndex)
            val yp = magnitude(real, imag, peakIndex + 1)
            val denom = ym - 2.0 * y0 + yp
            if (kotlin.math.abs(denom) > 1e-12) (0.5 * (ym - yp) / denom).coerceIn(-1.0, 1.0) else 0.0
        } else 0.0

        val frequency = (peakIndex + delta) * sampleRate.toDouble() / n.toDouble()
        return Peak(
            frequencyHz = frequency,
            magnitude = peakMagnitude,
            peakToMedianDb = db,
            fftSize = n
        )
    }

    private fun magnitude(real: DoubleArray, imag: DoubleArray, i: Int): Double =
        sqrt(real[i] * real[i] + imag[i] * imag[i])

    private fun highestPowerOfTwoAtMost(value: Int): Int {
        var n = 1
        while (n <= value / 2) n = n shl 1
        return n
    }

    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle)
            val wLenI = sin(angle)
            var base = 0
            while (base < n) {
                var wr = 1.0
                var wi = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val uR = real[base + k]
                    val uI = imag[base + k]
                    val vIndex = base + k + half
                    val vR = real[vIndex] * wr - imag[vIndex] * wi
                    val vI = real[vIndex] * wi + imag[vIndex] * wr
                    real[base + k] = uR + vR
                    imag[base + k] = uI + vI
                    real[vIndex] = uR - vR
                    imag[vIndex] = uI - vI
                    val nextWr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextWr
                }
                base += len
            }
            len = len shl 1
        }
    }
}
