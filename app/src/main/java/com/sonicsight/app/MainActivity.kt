package com.sonicsight.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import com.sonicsight.app.databinding.ActivityMainBinding
import com.sonicsight.app.video.VideoVibrationAnalyzer
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalCamera2Interop::class)
class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private var recording: Recording? = null
    private var recorder: Recorder? = null
    private var lastClip: File? = null
    private var selectedFps = 60
    private var capabilityReport: CameraCapabilityReport? = null
    private var timingCollector: CameraTimingCollector? = null
    private var lastRoi = RectF(0.2f, 0.28f, 0.8f, 0.62f)
    private val worker = Executors.newSingleThreadExecutor()

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else binding.statusText.text = "Camera permission is required."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val report = CameraCapabilityProbe.rearCamera(this)
        capabilityReport = report
        binding.capabilityText.text = report?.let {
            val modes = it.highSpeed.take(6).joinToString { m -> "${m.first.width}×${m.first.height}@${m.second.upper}" }
            "Rear camera ${it.cameraId} • HFR: ${if (modes.isBlank()) "none reported" else modes} • OIS ${it.hasOis} • EIS ${it.hasVideoStabilization}"
        } ?: "No rear camera found"

        binding.recordButton.setOnClickListener {
            if (recording == null) beginRecording() else recording?.stop()
        }
        binding.analyzeButton.setOnClickListener { analyzeLastClip() }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        binding.statusText.text = "Starting camera…"
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            val cameraInfo = provider.getCameraInfo(selector)
            val camera2Info = Camera2CameraInfo.from(cameraInfo)
            val activeHeight = camera2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.height() ?: 0
            val collector = CameraTimingCollector(activeHeight)
            timingCollector = collector
            val previewBuilder = Preview.Builder()
                .setPreviewStabilizationEnabled(false)
            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(collector)
            val preview = previewBuilder.build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            val highCaps = Recorder.getHighSpeedVideoCapabilities(cameraInfo)
            if (highCaps != null) {
                val quality = highCaps.getSupportedQualities(DynamicRange.SDR).firstOrNull()
                if (quality != null) {
                    val r = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(quality))
                        .build()
                    val vc = VideoCapture.Builder(r)
                        .setVideoStabilizationEnabled(false)
                        .build()
                    val probe = HighSpeedVideoSessionConfig.Builder(vc)
                        .setPreview(preview)
                        .build()
                    val ranges = cameraInfo.getSupportedFrameRateRanges(probe)
                    // A combined preview + HFR recording session needs a fixed range.
                    // Never synthesize [upper, upper] from a dynamic range unless the
                    // device actually reports that fixed range as supported.
                    val fixed = ranges
                        .filter { it.lower == it.upper && it.upper >= 120 }
                        .maxByOrNull { it.upper }
                    if (fixed != null) {
                        val config = HighSpeedVideoSessionConfig.Builder(vc)
                            .setPreview(preview)
                            .setFrameRateRange(fixed)
                            .setSlowMotionEnabled(false)
                            .build()
                        provider.bindToLifecycle(this, selector, config)
                        recorder = r
                        selectedFps = fixed.upper
                        binding.statusText.text = "Ready • ${selectedFps} FPS high-speed mode"
                        return@addListener
                    }
                }
            }

            // Fallback records standard video. Useful for initial rolling-shutter experiments.
            val r = Recorder.Builder().build()
            val vc = VideoCapture.Builder(r)
                .setVideoStabilizationEnabled(false)
                .build()
            provider.bindToLifecycle(this, selector, preview, vc)
            recorder = r
            selectedFps = 60
            binding.statusText.text = "Ready • standard video fallback"
        }, mainExecutor)
    }

    private fun beginRecording() {
        val r = recorder ?: return
        val file = File(cacheDir, "vibration-${System.currentTimeMillis()}.mp4")
        lastRoi = binding.roiOverlay.normalizedRoi()
        val pending = r.prepareRecording(this, FileOutputOptions.Builder(file).build())
        // DO NOT call withAudioEnabled(): this app has no microphone permission and records video only.
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    binding.recordButton.text = "Stop recording"
                    binding.statusText.text = "Recording video only • hold phone still"
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    binding.recordButton.text = "Record vibration clip"
                    if (!event.hasError()) {
                        lastClip = file
                        binding.analyzeButton.isEnabled = true
                        binding.statusText.text = "Captured ${file.name}. Keep the same ROI for analysis."
                    } else {
                        binding.statusText.text = "Recording failed: ${event.error}"
                    }
                }
            }
        }
    }

    private fun analyzeLastClip() {
        val clip = lastClip ?: return
        binding.analyzeButton.isEnabled = false
        binding.statusText.text = "Decoding and extracting row motion…"
        worker.execute {
            try {
                val wav = File(filesDir, "recovered-${System.currentTimeMillis()}.wav")
                val timing = timingCollector?.snapshot()
                val result = VideoVibrationAnalyzer().analyze(
                    input = clip,
                    roi = lastRoi,
                    outputWav = wav,
                    nominalFps = selectedFps,
                    measuredReadoutSkewNs = timing?.rollingShutterSkewNs,
                    exposureTimeNs = timing?.exposureTimeNs,
                    onProgress = { frames -> runOnUiThread { binding.statusText.text = "Analyzing… $frames frames" } }
                )
                val reportFile = File(filesDir, "experiment-${System.currentTimeMillis()}.json")
                ExperimentReportWriter.write(
                    file = reportFile,
                    capability = capabilityReport,
                    selectedFps = selectedFps,
                    roi = lastRoi,
                    result = result
                )
                runOnUiThread {
                    val peak = result.dominantFrequencyHz?.let { hz ->
                        " • peak %.1f Hz (%.1f dB)".format(hz, result.peakToMedianDb ?: 0.0)
                    } ?: ""
                    binding.statusText.text = "Recovered ${result.samples} samples @ ${result.sampleRate} Hz$peak • ${result.timingSource}"
                    binding.resultText.text = "Saved ${result.wav.name} + ${reportFile.name}"
                    binding.analyzeButton.isEnabled = true
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    binding.statusText.text = "Analysis failed: ${t.message}"
                    binding.analyzeButton.isEnabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        recording?.close()
        worker.shutdown()
        super.onDestroy()
    }
}
