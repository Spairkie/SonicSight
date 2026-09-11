# SonicSight architecture

SonicSight is an experimental Android visual microphone / optical vibrometry app. The microphone is never used. The acquisition path is intentionally split into capture and analysis because Android constrained high-speed camera sessions are optimized around preview and encoded video surfaces rather than CPU image-analysis surfaces.

## Pipeline

1. **Capability probe** — Camera2 enumerates constrained high-speed modes, stabilization support, and camera hardware characteristics.
2. **Video-only capture** — CameraX records standard or true HFR video. Slow-motion retiming is disabled so the encoded timestamps retain the high capture cadence.
3. **Timing metadata** — Camera2 interop collects exposure time and `SENSOR_ROLLING_SHUTTER_SKEW` when the HAL exposes it.
4. **Offline decode** — `MediaExtractor` + `MediaCodec` decode the video into CPU-readable YUV frames.
5. **ROI extraction** — Only luminance rows inside the selected region are processed.
6. **Sub-pixel motion** — Scanlines are compared against a reference using normalized cross-correlation and parabolic peak interpolation.
7. **Rolling-shutter timeline** — Each scanline receives a time offset based on the measured or estimated sensor readout skew. Frame gaps are preserved.
8. **Resampling** — The non-uniform timeline is interpolated onto regular PCM samples.
9. **Conditioning** — DC/very-low-frequency motion is removed and the waveform is normalized.
10. **Measurement** — An FFT reports the strongest spectral peak for controlled-tone validation.
11. **Artifacts** — The app writes a WAV plus a JSON experiment record locally on the device.

## Current scientific boundary

The first milestone is frequency recovery from a deliberately responsive visible target, not arbitrary conversation recovery. A valid result must survive controls: target damped, source off, rigid background ROI, and repeated source-frequency changes.

## Next algorithmic upgrades

- Per-device rolling-shutter calibration.
- Better frame-to-sensor timestamp alignment.
- Phase-based / complex-steerable-pyramid motion extraction.
- Multi-region consensus and rigid-background rejection.
- Exposure/focus locking tuned for microscopic motion.
- Objective SNR and repeatability metrics.
- Optional real-time low-FPS diagnostic mode separate from HFR offline analysis.
