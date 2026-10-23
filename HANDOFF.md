# HANDOFF — Zoomy Wallpaper (`com.zoomy.wallpaper`)

## 1. Project Overview & Origin
- **Zoomy Wallpaper** (`com.zoomy.wallpaper`) is a native 64-bit Android live wallpaper studio built with **Kotlin** and **Jetpack Compose (Material 3)**.
- **Inspiration:** Inspired by an older 32-bit (`armeabi-v7a`) Chinese Android app (`Blur + Zoom Effect`) for iOS-style unlock blur and zoom wallpaper effects. Because the original app relied on a 32-bit-only JNI library (`libImgFun.so`) that cannot run on modern 64-bit-only (`arm64-v8a`) Android devices, contained hardcoded Chinese strings, wrote every frame to disk as a full-resolution JPEG during encoding, and included Alipay/WeChat donation/promo screens, **Zoomy Wallpaper** was rebuilt from the ground up into a clean, modern, telemetry-free Android project.
- **Current Version:** `1.1.0` (`versionCode = 2`)
- **SDK Targets:** `minSdk = 26` (Android 8.0), `targetSdk = 35` / `compileSdk = 35` (Android 15), JVM Target `17`, Kotlin `2.1.10`, AGP `8.8.1`.

---

## 2. Package & File Structure (`app/src/main/java/com/zoomy/wallpaper/`)

- **`ZoomyApp.kt`** — `Application` entry point initializing `AppUtils`.
- **`service/ZoomyWallpaperService.kt`** — `WallpaperService` & `HybridWallpaperEngine` supporting both the **Real-Time 120Hz Choreographer Canvas Engine** (`engineMode = 0`) and the **Hardware MP4 Video Engine** (`engineMode = 1`).
- **`encoder/`**
  - `WallpaperCanvasRenderer.kt` — Hardware-accelerated `Canvas` renderer shared by both the Real-Time Engine and the MP4 Video Encoder. Handles source rect cropping, mip-level crossfading, `ColorMatrixColorFilter` (iOS "Color Wake" saturation/brightness bloom), uniform dimming, and top/bottom `LinearGradient` scrims.
  - `WallpaperVideoGenerator.kt` — Coroutine-based multi-core video generator (`WallpaperRenderSpec` + `generateVideo`).
  - `FrameEncoder.kt`, `EncoderConfig.kt`, `AvcEncoderConfig.kt`, `HevcEncoderConfig.kt`, `FrameMuxer.kt`, `Mp4FrameMuxer.kt` — `MediaCodec` input `Surface` + `MediaMuxer` pipeline.
- **`model/`**
  - `WallpaperPreferences.kt` — Centralized typed preferences manager backed by `PrefsStore` (`zoomy_prefs`).
  - `BezierControlPoints.kt` — Serializable `(x1, y1, x2, y2)` cubic Bézier control points.
- **`util/`**
  - `StackBlurEngine.kt` — Zero-allocation, in-place Mario Klingemann StackBlur implementation using thread-local reusable `BlurWorkspace` buffers (`pix`, `vmin`, `stack`) and automatic downscaling (`fastBlurScaled`).
  - `CubicBezierInterpolator.kt` — 4096-step cubic Bézier `Interpolator`.
  - `AppUtils.kt` — Screen metrics, `PrefsStore`, `ToastUtils`, `FileUtils`, and `ImageUtils` (direct `File` decoding without legacy `MediaStore` `_data` hacks).
- **`ui/`**
  - `MainActivity.kt` — ComponentActivity hosting the Compose UI, Android Photo Picker (`PickVisualMedia`), one-tap System Wallpaper importer (`WallpaperManager`), rotation, and video generation coroutine launcher.
  - `WallpaperStudioScreen.kt` — Main Material 3 studio interface, interactive drag-to-pan phone preview card (`AnimatedWallpaperPreviewBox`), and the `ModalBottomSheet` **Animation Curve Studio** (`CurveEditorSheetContent`).
  - `SettingsScreen.kt` — Dedicated Settings screen with **Appearance & Theme** (`System`, `Light`, `Dark`, `Pure AMOLED Black`), **Engine & Compatibility** toggles, **Reset All Parameters**, and **About / Developer (`@areyouanand`)**.
  - `theme/Theme.kt` — `ZoomyTheme` with Material You dynamic color and `#000000` AMOLED black override.

---

## 3. Key Technical Decisions & Gotchas (Do Not Regress)

1. **In-Place `StackBlurEngine` & `BlurWorkspace` (`util/StackBlurEngine.kt`)**
   - Never allocate `IntArray(w * h * 3)` inside the blur loop: at 1440p across multiple coroutine worker threads, allocating `w * h * 3` 32-bit ints (~55 MB per thread) causes an immediate `OutOfMemoryError`.
   - `StackBlurEngine.fastBlurInPlace` packs the horizontal-pass RGB values directly back into the 32-bit `workspace.pix` array before running the vertical pass, and reuses `ThreadLocal<BlurWorkspace>` across frames.
2. **Android `SurfaceHolder` Producer Lock (`surfaceLockedByCanvas` in `ZoomyWallpaperService.kt`)**
   - On Android, once `holder.lockHardwareCanvas()` has been called on a `SurfaceHolder`, its native `IGraphicBufferProducer` is permanently bound to `Canvas`/`HWUI` until that `Surface` is destroyed and recreated.
   - Calling `MediaPlayer.setSurface(holder.surface)` + `prepare()` on a Canvas-locked `Surface` throws `java.lang.IllegalStateException`.
   - `HybridWallpaperEngine` tracks `surfaceLockedByCanvas` per `SurfaceHolder` lifecycle (`onSurfaceCreated` / `onSurfaceDestroyed`) and never attaches `MediaPlayer` to an already Canvas-bound surface.
3. **Do Not Use `WallpaperService.Engine.onZoomChanged` to Clamp Minimum Blur**
   - On ColorOS / OxygenOS (`CPH2841`), the stock launcher reports `onZoomChanged(1.0f)` and does not reset it to `0.0f` on the home screen. Using `launcherDepthZoom` to clamp `blurRemaining` leaves the wallpaper permanently blurred on OxygenOS. Keep `blurRemaining` driven strictly by the animation progress timer (`rawT` from `0f` to `1f`), and always finish `doFrame` with `drawRealTimeFrame(1f)`.
4. **Zero-Latency iOS Unlock Timing, Unified Spring Curve & Perceptual Blur Mip-Levels (`ZoomyWallpaperService.kt`)**
   - **Never block `onVisibilityChanged(true)` on `KeyguardManager.isKeyguardLocked`:** On OxygenOS/ColorOS when bound as `mWhich = 3` (`FLAG_SYSTEM | FLAG_LOCK`), `onVisibilityChanged(true)` fires at `t = 0 ms` while `isKeyguardLocked` stays `true` for another ~300 ms during the lockscreen fade-out. Blocking on `isKeyguardLocked` freezes the wallpaper in blur for 300 ms. Instead, start immediately on `onVisibilityChanged(true)` (drawing Frame `0` synchronously) and use a `650 ms` deduplication window (`lastAnimStartUptimeMs`) so the trailing `KeyguardLockedStateListener(false)` / `Intent.ACTION_USER_PRESENT` callback does not reset a running unlock animation mid-flight.
   - **Unified Blur + Zoom Curve (`(0.16, 1.0, 0.30, 1.0)`):** Mode 1 uses Apple's iOS spring-out cubic Bézier `(0.16, 1.0, 0.30, 1.0)` (`Y1 = 1.0`, eliminating initial S-curve hesitation) and drives **both** `totalInset` (zoom) and `blurRemaining` in 1:1 lockstep so unblurring and zooming launch and settle together.
   - **Perceptual Mip-Level Spacing (`(i / 9)^2.2`):** The Real-Time Engine precomputes 10 perceptually spaced blur mip-levels (`r = [0, 1, 5, 11, 21, 35, 53, 76, 106, 180]` for `startBlur = 180`) rather than linear radii (`51..180` all look like an opaque color wash where zoom motion is invisible).
5. **ADB Install on ColorOS / OxygenOS**
   - If `adb install` hangs waiting for Play Protect verifier timeout (`com.google.android.verifier`), keep `adb shell settings put global verifier_verify_adb_installs 0` set or use `adb push ... /data/local/tmp/zoomy.apk && adb shell cmd package install -r /data/local/tmp/zoomy.apk`.

---

## 4. Building & Installing via CLI

```powershell
$env:JAVA_HOME = (Get-ChildItem "F:\AI stuff\HeadFi-Org\.toolchain\jdk-17*" | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug

$adb = "F:\AI stuff\DX270\platform-tools\adb.exe"
& $adb push "F:\AI stuff\ios wall\app\build\outputs\apk\debug\app-debug.apk" /data/local/tmp/zoomy.apk
& $adb shell cmd package install -r /data/local/tmp/zoomy.apk
& $adb shell am start -n com.zoomy.wallpaper/com.zoomy.wallpaper.ui.MainActivity
```
