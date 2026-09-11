# SonicSight development setup

## Recommended workstation

- Current Android Studio
- JDK 17
- Android SDK Platform 37 (`platforms;android-37.0`)
- Android SDK Build-Tools 37.0.0
- Platform Tools / ADB
- Gradle 9.5.0 until the standard wrapper files are generated and committed

Android 17 uses a minor-versioned SDK package name. In a headless environment the packages are:

```bash
sdkmanager --channel=3 "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
```

The Gradle configuration still uses `compileSdk = 37` and `targetSdk = 37`.

## Pixel setup

1. Enable **Developer options** on the Pixel.
2. Enable **USB debugging**.
3. Connect over USB for the first session and accept the workstation's RSA debugging prompt on the phone.
4. Verify the device is visible:

```bash
adb devices -l
```

5. Record the exact device/software state before experiments:

```bash
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
```

Do not change camera app settings or the Pixel software version in the middle of a comparison series without noting it in the experiment record.

## Build

From the repository root:

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions also publishes the latest successful debug APK as the `sonicsight-debug` workflow artifact.

## Install and launch

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.sonicsight.app
adb shell monkey -p com.sonicsight.app -c android.intent.category.LAUNCHER 1
```

The first launch should request **Camera** permission only. If Android reports a microphone permission request, stop the test and treat it as a regression.

## First device session

Before attempting speech reconstruction:

1. Confirm the capability line in SonicSight reports the expected rear camera and any 120/240 FPS modes.
2. Run one standard-video source-off capture to establish the noise floor.
3. Run 100 Hz, 200 Hz, and 440 Hz tone captures with a lightweight target under bright steady light.
4. Keep the phone mechanically fixed and repeat each condition three times.
5. Preserve each generated experiment JSON and WAV.
6. Compare the target ROI against a rigid-background ROI.

The detailed experiment protocol is in [`../TESTING.md`](../TESTING.md).

## Useful ADB diagnostics

Basic camera service state:

```bash
adb shell dumpsys media.camera > camera-dumpsys.txt
```

Package/permission state:

```bash
adb shell dumpsys package com.sonicsight.app > sonicsight-package.txt
```

Recent app logs:

```bash
adb logcat -c
adb logcat --pid=$(adb shell pidof -s com.sonicsight.app)
```

If a capture or decoder path fails on the Pixel, keep the exact failure text plus the device report. Camera HAL behavior is device-specific, so those two pieces of information are much more useful than a screenshot alone.

## Measurement discipline

SonicSight intentionally disables preview/video stabilization during capture because stabilization can suppress or transform the motion being measured. For the same reason, avoid handheld comparisons. The eventual measurement mode will also expose/lock focus and exposure only after we validate which Camera2 request controls remain compatible with the Pixel's high-speed session.
