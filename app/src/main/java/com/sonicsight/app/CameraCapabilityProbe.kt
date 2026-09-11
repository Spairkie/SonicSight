package com.sonicsight.app

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size

/** Camera2-level capability report used before CameraX binds a recording session. */
data class CameraCapabilityReport(
    val cameraId: String,
    val hardwareLevel: Int?,
    val highSpeed: List<Pair<Size, Range<Int>>>,
    val hasOis: Boolean,
    val hasVideoStabilization: Boolean
) {
    val bestFps: Int? get() = highSpeed.maxOfOrNull { it.second.upper }
}

object CameraCapabilityProbe {
    fun rearCamera(context: Context): CameraCapabilityReport? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in manager.cameraIdList) {
            val c = manager.getCameraCharacteristics(id)
            if (c.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue

            val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val highSpeed = mutableListOf<Pair<Size, Range<Int>>>()
            if (capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) && map != null) {
                for (size in map.highSpeedVideoSizes) {
                    for (range in map.getHighSpeedVideoFpsRangesFor(size)) {
                        highSpeed += size to range
                    }
                }
            }

            val ois = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                ?.any { it == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON } == true
            val eis = c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                ?.any { it == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON } == true

            return CameraCapabilityReport(
                cameraId = id,
                hardwareLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL),
                highSpeed = highSpeed.sortedByDescending { it.second.upper },
                hasOis = ois,
                hasVideoStabilization = eis
            )
        }
        return null
    }
}
