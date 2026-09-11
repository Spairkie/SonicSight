package com.sonicsight.app

import android.graphics.RectF
import android.os.Build
import com.sonicsight.app.video.VideoVibrationAnalyzer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Writes a compact local-only JSON record for each analysis run. */
object ExperimentReportWriter {
    fun write(
        file: File,
        capability: CameraCapabilityReport?,
        selectedFps: Int,
        roi: RectF,
        result: VideoVibrationAnalyzer.Result
    ) {
        val highSpeed = JSONArray()
        capability?.highSpeed?.forEach { (size, range) ->
            highSpeed.put(
                JSONObject()
                    .put("width", size.width)
                    .put("height", size.height)
                    .put("fpsLower", range.lower)
                    .put("fpsUpper", range.upper)
            )
        }

        val root = JSONObject()
            .put("schemaVersion", 1)
            .put("app", "SonicSight")
            .put("device", JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("androidRelease", Build.VERSION.RELEASE)
                .put("sdkInt", Build.VERSION.SDK_INT))
            .put("camera", JSONObject()
                .put("cameraId", capability?.cameraId ?: JSONObject.NULL)
                .put("hardwareLevel", capability?.hardwareLevel ?: JSONObject.NULL)
                .put("hasOis", capability?.hasOis ?: false)
                .put("hasVideoStabilization", capability?.hasVideoStabilization ?: false)
                .put("selectedFps", selectedFps)
                .put("highSpeedModes", highSpeed))
            .put("roi", JSONObject()
                .put("left", roi.left.toDouble())
                .put("top", roi.top.toDouble())
                .put("right", roi.right.toDouble())
                .put("bottom", roi.bottom.toDouble()))
            .put("analysis", JSONObject()
                .put("frames", result.frames)
                .put("samples", result.samples)
                .put("sampleRate", result.sampleRate)
                .put("timingSource", result.timingSource)
                .put("readoutSkewNs", result.readoutSkewNs)
                .put("exposureTimeNs", result.exposureTimeNs ?: JSONObject.NULL)
                .put("dominantFrequencyHz", result.dominantFrequencyHz ?: JSONObject.NULL)
                .put("peakToMedianDb", result.peakToMedianDb ?: JSONObject.NULL)
                .put("wavPath", result.wav.name))

        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
    }
}
