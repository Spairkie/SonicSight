package com.sonicsight.app.video

import android.graphics.RectF
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.sonicsight.app.dsp.NonUniformResampler
import com.sonicsight.app.dsp.RollingShutterExtractor
import com.sonicsight.app.dsp.SignalConditioner
import com.sonicsight.app.dsp.SpectrumAnalyzer
import java.io.File
import kotlin.math.roundToInt

/**
 * Offline decoder/analyzer. Decoding after capture is deliberate: constrained Android
 * high-speed sessions are designed around preview and video-encoder surfaces rather than
 * arbitrary CPU ImageAnalysis at the high recording rate.
 */
class VideoVibrationAnalyzer {
    data class Result(
        val wav: File,
        val samples: Int,
        val sampleRate: Int,
        val frames: Int,
        val timingSource: String,
        val readoutSkewNs: Long,
        val exposureTimeNs: Long?,
        val dominantFrequencyHz: Double?,
        val peakToMedianDb: Double?
    )

    private data class LumaRows(
        val rows: Array<FloatArray>,
        val yCoordinates: IntArray,
        val imageHeight: Int
    )

    fun analyze(
        input: File,
        roi: RectF,
        outputWav: File,
        nominalFps: Int,
        measuredReadoutSkewNs: Long? = null,
        exposureTimeNs: Long? = null,
        estimatedReadoutFraction: Float = 0.75f,
        onProgress: (Int) -> Unit = {}
    ): Result {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                track = i
                format = f
                break
            }
        }
        require(track >= 0 && format != null) { "No video track" }
        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val values = ArrayList<Float>(128_000)
        val times = ArrayList<Long>(128_000)
        val rowExtractor = RollingShutterExtractor()
        var inputDone = false
        var outputDone = false
        var frameCount = 0
        val info = MediaCodec.BufferInfo()
        var previousPtsNs: Long? = null
        val frameDeltas = ArrayList<Long>(512)
        var chosenSkewNs: Long? = null

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        val image = decoder.getOutputImage(outIndex)
                        if (image != null) {
                            image.use {
                                val ptsNs = info.presentationTimeUs * 1_000L
                                previousPtsNs?.let { prev ->
                                    val d = ptsNs - prev
                                    if (d > 0) frameDeltas += d
                                }
                                previousPtsNs = ptsNs

                                val sampleRows = lumaRows(it, roi)
                                val shifts = rowExtractor.addFrame(sampleRows.rows)
                                if (shifts.isNotEmpty()) {
                                    val observedFramePeriod = median(frameDeltas)
                                        ?: (1_000_000_000L / nominalFps.coerceAtLeast(1))
                                    val fallbackSkew = (observedFramePeriod *
                                        estimatedReadoutFraction.coerceIn(0.2f, 0.98f)).toLong()
                                    val skew = (measuredReadoutSkewNs ?: fallbackSkew)
                                        .coerceIn(1L, (observedFramePeriod * 0.98).toLong().coerceAtLeast(1L))
                                    chosenSkewNs = skew

                                    for (i in shifts.indices) {
                                        val yFraction = sampleRows.yCoordinates[i].toDouble() /
                                            sampleRows.imageHeight.coerceAtLeast(1).toDouble()
                                        val rowTime = ptsNs + (yFraction * skew).toLong()
                                        values += shifts[i]
                                        times += rowTime
                                    }
                                }
                                frameCount++
                                if (frameCount % 8 == 0) onProgress(frameCount)
                            }
                        }
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outIndex, false)
                }
            }
        } finally {
            decoder.stop()
            decoder.release()
            extractor.release()
        }

        require(frameCount > 1 && values.size > 1) {
            "Decoder did not expose enough CPU-readable YUV frames on this device"
        }

        val monotonicValues = ArrayList<Float>(values.size)
        val monotonicTimes = ArrayList<Long>(times.size)
        var last = Long.MIN_VALUE
        for (i in times.indices) {
            val t = times[i]
            if (t > last) {
                monotonicTimes += t
                monotonicValues += values[i]
                last = t
            }
        }
        require(monotonicValues.size > 1) { "Unable to build a monotonic rolling-shutter timebase" }

        val resampled = NonUniformResampler.resample(
            values = monotonicValues.toFloatArray(),
            timesNs = monotonicTimes.toLongArray(),
            maxRate = 48_000
        )
        val conditioned = SignalConditioner.condition(resampled.samples, resampled.sampleRate)
        val spectralPeak = SpectrumAnalyzer.dominantPeak(conditioned, resampled.sampleRate)
        WavWriter.writeMono16(outputWav, conditioned, resampled.sampleRate)

        val finalSkew = chosenSkewNs ?: measuredReadoutSkewNs
            ?: (1_000_000_000L / nominalFps.coerceAtLeast(1) *
                estimatedReadoutFraction.coerceIn(0.2f, 0.98f)).toLong()
        return Result(
            wav = outputWav,
            samples = conditioned.size,
            sampleRate = resampled.sampleRate,
            frames = frameCount,
            timingSource = if (measuredReadoutSkewNs != null) "Camera2 measured skew" else "estimated skew",
            readoutSkewNs = finalSkew,
            exposureTimeNs = exposureTimeNs,
            dominantFrequencyHz = spectralPeak?.frequencyHz,
            peakToMedianDb = spectralPeak?.peakToMedianDb
        )
    }

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val copy = values.toLongArray().sortedArray()
        return copy[copy.size / 2]
    }

    private fun lumaRows(image: Image, roi: RectF): LumaRows {
        val plane = image.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height
        val left = (roi.left * w).roundToInt().coerceIn(0, w - 16)
        val right = (roi.right * w).roundToInt().coerceIn(left + 16, w)
        val top = (roi.top * h).roundToInt().coerceIn(0, h - 8)
        val bottom = (roi.bottom * h).roundToInt().coerceIn(top + 8, h)
        val stepY = maxOf(1, (bottom - top) / 256)
        val rows = ArrayList<FloatArray>()
        val ys = ArrayList<Int>()
        var y = top
        while (y < bottom) {
            val row = FloatArray(right - left)
            var x = left
            var j = 0
            while (x < right) {
                val index = y * rowStride + x * pixelStride
                row[j++] = (buf.get(index).toInt() and 0xFF).toFloat()
                x++
            }
            rows += row
            ys += y
            y += stepY
        }
        return LumaRows(rows.toTypedArray(), ys.toIntArray(), h)
    }
}
