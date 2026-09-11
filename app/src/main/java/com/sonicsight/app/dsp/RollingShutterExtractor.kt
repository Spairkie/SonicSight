package com.sonicsight.app.dsp

/**
 * Experimental rolling-shutter signal extractor.
 * Each ROI row contributes one displacement sample per frame.
 *
 * IMPORTANT: the exact sample times depend on the sensor readout skew and frame gap.
 * The timebase is assigned by VideoVibrationAnalyzer after the row motion is extracted.
 */
class RollingShutterExtractor(
    private val maxShiftPx: Int = 3
) {
    private var referenceRows: Array<FloatArray>? = null

    fun reset() { referenceRows = null }

    fun addFrame(rows: Array<FloatArray>): FloatArray {
        val ref = referenceRows
        if (ref == null) {
            referenceRows = Array(rows.size) { rows[it].copyOf() }
            return FloatArray(0)
        }
        require(ref.size == rows.size)
        return FloatArray(rows.size) { y ->
            RowShiftEstimator.estimate(ref[y], rows[y], maxShiftPx)
        }
    }
}
