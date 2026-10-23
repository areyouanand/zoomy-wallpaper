# Zoomy Wallpaper

> [!NOTE]
> **Origin & Inspiration:** **Zoomy Wallpaper** was inspired by an older 32-bit (`armeabi-v7a`) Chinese Android app created for iOS-style blur and zoom unlock wallpaper effects. Because modern 64-bit-only Android flagships (`arm64-v8a`) can no longer run legacy 32-bit native libraries, this project was built as a clean, 64-bit-native **Kotlin + Jetpack Compose Material 3** live wallpaper studio with a **Real-Time 120Hz `Choreographer` Engine**, a **Multi-Core Hardware `MediaCodec` MP4 Engine**, smart unlock-only triggers, iOS "Color Wake" bloom, and **zero telemetry, trackers, or internet permissions**.

---

## Features

### 1. Dual Live Wallpaper Engines
- **Real-Time 120Hz Engine (Default — Instant Apply):**
  - Pre-computes progressive blur mip-levels in the background and renders directly onto the wallpaper `SurfaceHolder` at your display's native refresh rate (60Hz / 90Hz / 120Hz) via `Choreographer`.
  - **Instant Live Updates:** Tweak any slider or curve and tap **Update Live** to update your active home screen wallpaper immediately without waiting for video encoding.
  - **Home Screen Swipe Parallax (`0 – 100%`):** Smooth horizontal pan (`onOffsetsChanged`) as you swipe across home screen pages.
- **Hardware MP4 Video Engine (`HEVC / H.265` & `AVC / H.264`):**
  - Multi-core `StackBlurEngine` + zero-allocation hardware `MediaCodec` `Surface` encoding.
  - Configurable **60 / 90 / 120 FPS** and bitrate (`Mbps`).

### 2. Smart Trigger Conditions & Timing
- **Every Home Return:** Plays the blur-to-clear + zoom transition whenever the home screen becomes visible (both when unlocking and when swiping home from apps).
- **Device Unlock Only:** Uses Android 13+ `KeyguardManager.KeyguardLockedStateListener` (direct Binder callback) + `Intent.ACTION_USER_PRESENT` with trigger deduplication so the full animation plays **only when unlocking the device**, staying settled on the sharp resting frame when switching apps.
- **Playback Start Delay (`0 – 500 ms`):** Syncs the start of the animation with your device's fingerprint/lockscreen fade transition (or `0 ms` synchronous start).

### 3. iOS-Style Visual Polish
- **Zoom Modes:**
  - **Zoom Out → 1× (iOS Unlock):** Starts zoomed-in and smoothly settles to `1×`.
  - **1× → Zoom In:** Starts at `1×` and zooms in.
  - **Zoom Pulse:** Smooth zoom-in and settle back to `1×`.
- **Interactive Animation Curve Studio:**
  - 3 built-in presets (*Mode 1: iOS Spring Ease-Out `(0.16, 1.0, 0.30, 1.0)`*, *Mode 2: Linear-Out Slow-In*, *Mode 3: Fast-Out Slow-In*) + a **Custom 4-Point Cubic Bézier Editor** (`X1, Y1, X2, Y2`) with side-by-side live curve graph and live wallpaper preview.
- **Gaussian Blur & Resting Home Blur:**
  - Configurable **Blur Duration (`0 – 100%`)**, **Start Blur Intensity (`0 – 180`)**, and **Resting Home Screen Blur (`0 – 100`)** (like iOS's *"Blur"* home screen wallpaper toggle).
- **iOS "Color Wake" Bloom (`0 – 100%`):**
  - Starts slightly muted/cooled during the heavy blur phase and blooms into full saturation and brightness as the wallpaper unblurs.
- **Dimming & Edge Gradient Scrims:**
  - **Start Unlock Dimming (`0 – 60%`)** & **Resting Home Screen Dimming (`0 – 60%`)**
  - **Auto Extra Dim in System Dark Mode (`0 – 60%`)**
  - **Top Status Bar Scrim (`0 – 80%`)** & **Bottom Dock Scrim (`0 – 80%`)** for crisp icon and clock legibility without darkening the center of your wallpaper.

### 4. Interactive Preview & Studio Workflow
- **Drag-to-Pan Crop:** Drag directly on the phone preview card to frame your wallpaper (`panX`, `panY`), with **Rotate 90°** and **Center Crop** actions.
- **One-Tap System Wallpaper Import:** Grab your current static system wallpaper directly or pick any image from your gallery.
- **Material 3 Theme Modes:** **System Default**, **Light Mode**, **Dark Mode**, and **Pure AMOLED Black (`#000000`)** (in **Settings**).
- **100% Offline & Private:** Zero internet permissions, zero ads, zero telemetry.

---

## Building from Source

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Developer & License

- **Developer:** [@areyouanand](https://github.com/areyouanand)
- **License:** [MIT License](LICENSE)
