# SonicSight on-device test protocol

The first goal is **not speech**. The first goal is to prove that a normal Android camera preserves a known mechanical vibration strongly enough for the app to recover the correct frequency.

## 1. Build and install

1. Open the project in a current Android Studio with JDK 17 and Android SDK 37 installed.
2. Let Gradle sync and resolve CameraX 1.6.2.
3. Run on a physical Android device (API 28+). Do not use an emulator for camera measurements.
4. Confirm Android only asks for **Camera** permission. The manifest intentionally contains no `RECORD_AUDIO` or `INTERNET` permission.

## 2. Prepare a target

Use a lightweight, visibly textured target such as flexible packaging, thin paper, foil, a lightweight cup wall, or a visible speaker cone. Bright, steady illumination matters because short exposure and high spatial contrast improve tiny-motion estimation.

Brace the phone on a rigid support. Fill a substantial part of the rear-camera view with the target and place the ROI over strong texture/edges. Avoid touching the phone during a recording.

## 3. Run controlled tones

Use a separate normal sound source in the room to play a known pure tone. The SonicSight app never reads microphone audio; the known generator frequency is simply the ground-truth label.

Start with this sequence:

| Tone | Purpose |
| --- | --- |
| 80–100 Hz | easiest mechanical sanity check |
| 200 Hz | transition above very-low-frequency phone motion |
| 440 Hz | first strong optical-vibrometry milestone |
| 800 Hz | tests row timing/exposure/compression |
| 1.2 kHz | meaningful rolling-shutter milestone |
| 2 kHz | stretch target; highly device/light/exposure dependent |

Record 5–10 seconds per tone. Repeat each tone at least three times before drawing conclusions.

## 4. Required controls

For every promising result, also record:

- the same target with the tone stopped;
- the tone playing while the target is mechanically damped;
- a rigid background ROI rather than the vibrating target;
- the same target under brighter steady illumination;
- both standard and HFR modes when the device exposes them.

A valid signal should follow the source frequency and be stronger on the responsive target than on the rigid-background/control captures. A peak that appears everywhere is more likely lighting flicker, camera shake, codec behavior, or camera-module vibration.

## 5. Pass gate for the first milestone

Phase 1 passes when the dominant recovered peak lands within roughly 2% of the known 100/200/440 Hz tone on three consecutive recordings for at least one target, and the peak responds correctly when the source frequency changes.

Treat results marked with **estimated timing** as exploratory. Frequency calibration should rely on measured rolling-shutter skew when the camera exposes it or on a future per-device calibration procedure.

## 6. What to capture when reporting a device result

Record the phone model, Android version, camera ID, selected resolution/FPS, whether HFR was available, timing source (measured/estimated), rolling-shutter skew, exposure duration, target material, lighting conditions, tone frequency, and recovered peak. Those measurements will drive the next implementation step much more reliably than subjective listening alone.

## 7. Experiment artifacts

Each completed analysis creates a WAV file plus a JSON experiment record. The JSON contains the phone model, Android/API version, camera capability report, selected FPS, ROI, timing source, readout skew, exposure time, recovered sample rate, dominant frequency, and spectral peak-to-median ratio. Keep the JSON beside the test notes so results can be compared across camera modes and devices.
