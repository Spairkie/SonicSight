package com.sonicsight.app

import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import java.util.ArrayDeque

/**
 * Read-only Camera2 metadata collector used through CameraX interop.
 * It records rolling-shutter skew and exposure timing without modifying the capture session.
 */
class CameraTimingCollector(
    private val activeArrayHeight: Int,
    private val capacity: Int = 128
) : CameraCaptureSession.CaptureCallback() {
    data class Snapshot(
        val rollingShutterSkewNs: Long?,
        val exposureTimeNs: Long?,
        val sensorTimestampNs: Long?,
        val cropRegion: Rect?
    )

    private val skews = ArrayDeque<Long>()
    private val exposures = ArrayDeque<Long>()
    @Volatile private var latestTimestamp: Long? = null
    @Volatile private var latestCrop: Rect? = null

    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
    ) {
        result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)?.let { raw ->
            // Android reports skew for the active array. Scale only for an actual sensor crop,
            // not for mere video down-scaling of the full field of view.
            val crop = result.get(CaptureResult.SCALER_CROP_REGION)
            latestCrop = crop
            val fraction = if (crop != null && activeArrayHeight > 0) {
                (crop.height().toDouble() / activeArrayHeight.toDouble()).coerceIn(0.05, 1.0)
            } else 1.0
            addBounded(skews, (raw * fraction).toLong().coerceAtLeast(1L))
        }
        result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { addBounded(exposures, it) }
        latestTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        rollingShutterSkewNs = median(skews),
        exposureTimeNs = median(exposures),
        sensorTimestampNs = latestTimestamp,
        cropRegion = latestCrop
    )

    @Synchronized
    private fun addBounded(q: ArrayDeque<Long>, value: Long) {
        if (q.size >= capacity) q.removeFirst()
        q.addLast(value)
    }

    private fun median(q: ArrayDeque<Long>): Long? {
        if (q.isEmpty()) return null
        val a = q.toLongArray().sortedArray()
        return a[a.size / 2]
    }
}
