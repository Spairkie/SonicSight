import com.sonicsight.app.dsp.NonUniformResampler
import com.sonicsight.app.dsp.RowShiftEstimator
import com.sonicsight.app.dsp.SignalConditioner
import com.sonicsight.app.dsp.SpectrumAnalyzer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

fun sampleSignal(n: Int, shift: Double): FloatArray = FloatArray(n) { i ->
    val x = i - shift
    (0.65 * sin(2 * PI * x / 17.0) + 0.3 * sin(2 * PI * x / 7.0)).toFloat()
}

fun main() {
    val ref = sampleSignal(128, 0.0)
    val cur = sampleSignal(128, 0.72)
    val estimated = RowShiftEstimator.estimate(ref, cur, 3)
    println("Known shift ~= 0.72 px; estimated = %.3f px".format(estimated))
    check(abs(abs(estimated) - 0.72f) < 0.25f)

    val raw = FloatArray(4800) { i -> (0.2 + sin(2 * PI * 440 * i / 48000.0)).toFloat() }
    val conditioned = SignalConditioner.condition(raw, 48000)
    val peak = conditioned.maxOf { abs(it) }
    println("Conditioned peak = %.3f".format(peak))
    check(peak in 0.95f..0.99f)
    val spectralPeak = SpectrumAnalyzer.dominantPeak(conditioned, 48000)!!
    println("Recovered spectral peak = %.2f Hz (%.1f dB over median)".format(spectralPeak.frequencyHz, spectralPeak.peakToMedianDb))
    check(abs(spectralPeak.frequencyHz - 440.0) < 3.0)

    val t = longArrayOf(0, 20_000, 40_000, 60_000, 300_000, 320_000, 340_000)
    val v = FloatArray(t.size) { i -> sin(2 * PI * 1000 * t[i] / 1e9).toFloat() }
    val resampled = NonUniformResampler.resample(v, t, maxRate = 48_000)
    println("Non-uniform resampler: ${resampled.samples.size} samples @ ${resampled.sampleRate} Hz")
    check(resampled.sampleRate in 8_000..48_000)
    check(resampled.samples.size >= 2)
    println("DSP smoke tests passed")
}
