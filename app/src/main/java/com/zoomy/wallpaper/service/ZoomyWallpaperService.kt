package com.zoomy.wallpaper.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.animation.Interpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.zoomy.wallpaper.encoder.WallpaperCanvasRenderer
import com.zoomy.wallpaper.encoder.WallpaperVideoGenerator
import com.zoomy.wallpaper.model.WallpaperPreferences
import com.zoomy.wallpaper.util.AppUtils
import com.zoomy.wallpaper.util.CubicBezierInterpolator
import com.zoomy.wallpaper.util.ImageUtils
import com.zoomy.wallpaper.util.PrefsStore
import com.zoomy.wallpaper.util.StackBlurEngine
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class ZoomyWallpaperService : WallpaperService() {

    private var baseDirPath: String? = null

    override fun onCreateEngine(): Engine {
        val externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        baseDirPath = externalFilesDir?.absolutePath ?: filesDir.absolutePath
        return HybridWallpaperEngine()
    }

    inner class HybridWallpaperEngine : Engine(),
        MediaPlayer.OnErrorListener,
        Choreographer.FrameCallback,
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val backgroundExecutor = Executors.newSingleThreadExecutor()
        private val prefs = WallpaperPreferences.getInstance()

        // Track whether the current SurfaceHolder has been locked by Canvas
        // (Android does not allow MediaPlayer.setSurface on a Surface already locked by Canvas)
        private var surfaceLockedByCanvas = false

        // MediaPlayer state (used when engineMode == 1 and surface is not locked by Canvas)
        private var mediaPlayer: MediaPlayer? = null
        private var videoDurationMs: Int = 1000
        private var isPausedForVisibility = true

        // Real-Time 120Hz Canvas state (used when engineMode == 0)
        @Volatile
        private var blurMipLevels: List<Bitmap> = emptyList()
        private var canvasRenderer: WallpaperCanvasRenderer? = null
        private var isAnimatingRealTime = false
        private var animStartTimeMs = 0L
        private var animDurationMs = 1000L
        private var currentAnimProgress = 1f // 0f = start of unlock, 1f = settled sharp home screen
        private var launcherXOffset = 0.5f   // 0f..1f from onOffsetsChanged

        // Cached interpolator for zero per-frame allocations
        private var cachedCurveInterpolator: Interpolator? = null

        // Fast unlock detection & deduplication state
        private var wasKeyguardLocked = false
        private var lastAnimStartUptimeMs = 0L
        private var receiverRegistered = false
        private var keyguardListener: Any? = null
        private var lastLoadedConfigTimestamp = -1L
        private var lastKeyguardLockedState = false
        private val keyguardStatePollRunnable = object : Runnable {
            override fun run() {

                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val lockedNow = km?.isKeyguardLocked == true
                if (lockedNow && !lastKeyguardLockedState) {
                    // transitioned to locked (screen off / lock)

                    wasKeyguardLocked = true
                    handler.removeCallbacksAndMessages(null)
                    stageStartFrame()
                } else if (!lockedNow && lastKeyguardLockedState) {
                    // transitioned to unlocked (fingerprint success) -> trigger animation ASAP

                    onKeyguardUnlockedNow()
                }
                lastKeyguardLockedState = lockedNow
                // Periodic heartbeat log (every ~1s) to confirm poller is alive and reading state

                handler.postDelayed(this, 8L)
            }
        }

        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {

                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        wasKeyguardLocked = true
                        lastKeyguardLockedState = true
                        handler.removeCallbacks(startAnimationRunnable)
                        handler.removeCallbacks(pauseAnimationRunnable)
                        stageStartFrame()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (km?.isKeyguardLocked == true) {
                            wasKeyguardLocked = true
                            lastKeyguardLockedState = true
                            handler.removeCallbacks(startAnimationRunnable)
                            handler.removeCallbacks(pauseAnimationRunnable)
                            stageStartFrame()
                        } else {
                            onKeyguardUnlockedNow()
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        onKeyguardUnlockedNow()
                    }
                }
            }
        }

        private fun startKeyguardPolling() {
            // continuous polling already started in onCreate; nothing to do here
        }

        private val startAnimationRunnable = Runnable {
            lastAnimStartUptimeMs = SystemClock.uptimeMillis()
            if (shouldUseRealTimeCanvas()) {
                startRealTimeAnimation()
            } else {
                startVideoPlayback()
            }
        }

        private val pauseAnimationRunnable = Runnable {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val lockedNow = km?.isKeyguardLocked == true
            if (lockedNow) {
                wasKeyguardLocked = true
            }
            if (lockedNow || prefs.isRewindOnHide()) {
                stageStartFrame()
            } else if (shouldUseRealTimeCanvas()) {
                stopRealTimeAnimation()
                currentAnimProgress = 1f
                drawRealTimeFrame(1f)
            } else {
                val mp = mediaPlayer ?: return@Runnable
                try {
                    mp.pause()
                } catch (_: Throwable) {
                }
                isPausedForVisibility = true
            }
        }

        private fun shouldUseRealTimeCanvas(): Boolean {
            return prefs.getEngineMode() == 0 || surfaceLockedByCanvas
        }

        /**
         * Pre-stages Frame 0 (full start blur + start zoom, or video frame 0) when screen turns off or hides
         * so the wallpaper surface is already holding the blurred/zoomed state before the next unlock.
         */
        private fun stageStartFrame() {
            if (shouldUseRealTimeCanvas()) {
                stopRealTimeAnimation()
                currentAnimProgress = 0f
                drawRealTimeFrame(0f)
            } else {
                val mp = mediaPlayer ?: return
                try {
                    mp.pause()
                    mp.seekTo(0)
                } catch (_: Throwable) {
                }
                isPausedForVisibility = true
            }
        }

        /**
         * Called when keyguard transitions from locked -> unlocked.
         * Deduplicates against onVisibilityChanged(true) if the animation already started < 650ms ago
         * during a direct fingerprint unlock.
         */
        private fun onKeyguardUnlockedNow() {
            val sinceLastStart = SystemClock.uptimeMillis() - lastAnimStartUptimeMs
            wasKeyguardLocked = false
            // Skip if an animation is already running or started recently, to avoid restarting mid-flight.
            if (isAnimatingRealTime || sinceLastStart < 1500L) {
                return
            }
            // Start the animation clock as early as possible (even before the wallpaper
            // is composited as visible) so it is already mid-flight when the lockscreen
            // fade-out reveals it, eliminating the perceived "delay before unblur".
            schedulePlayAnimation(forceFullAnimation = true)
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(true)
            PrefsStore.getInstance().sp.registerOnSharedPreferenceChangeListener(this)

            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            wasKeyguardLocked = km?.isKeyguardLocked == true
            lastKeyguardLockedState = wasKeyguardLocked
            // Start continuous keyguard-state polling (primary trigger mechanism).
            handler.post(keyguardStatePollRunnable)

            // Register fast Binder-based KeyguardLockedStateListener on Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && km != null) {
                try {
                    val listener = KeyguardManager.KeyguardLockedStateListener { isKeyguardLocked ->
                        if (isKeyguardLocked) {
                            wasKeyguardLocked = true
                        } else {
                            onKeyguardUnlockedNow()
                        }
                    }
                    km.addKeyguardLockedStateListener(mainExecutor, listener)
                    keyguardListener = listener
                } catch (_: Throwable) {
                }
            }

            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(unlockReceiver, filter)
                }
                receiverRegistered = true
            } catch (_: Throwable) {
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            Choreographer.getInstance().removeFrameCallback(this)
            PrefsStore.getInstance().sp.unregisterOnSharedPreferenceChangeListener(this)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val listener = keyguardListener as? KeyguardManager.KeyguardLockedStateListener
                if (km != null && listener != null) {
                    try {
                        km.removeKeyguardLockedStateListener(listener)
                    } catch (_: Throwable) {
                    }
                }
                keyguardListener = null
            }

            handler.removeCallbacks(keyguardStatePollRunnable)
            handler.removeCallbacks(keyguardStatePollRunnable)
            if (receiverRegistered) {
                try {
                    unregisterReceiver(unlockReceiver)
                } catch (_: Throwable) {
                }
                receiverRegistered = false
            }
            releaseMediaPlayer()
            backgroundExecutor.shutdownNow()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            cachedCurveInterpolator = null
            if (key == "configTimestamp" || key == "path" || key == "engineMode" || key == "rotate") {
                handler.post {
                    reloadEngineAssets()
                }
            } else if (shouldUseRealTimeCanvas() && isVisible && !isAnimatingRealTime) {
                handler.post {
                    drawRealTimeFrame(currentAnimProgress)
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceLockedByCanvas = false
            val frame = holder.surfaceFrame
            val w = if (frame.width() > 0) frame.width() else AppUtils.getScreenWidth()
            val h = if (frame.height() > 0) frame.height() else AppUtils.getScreenHeight()
            canvasRenderer = WallpaperCanvasRenderer(w, h)
            reloadEngineAssets()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width > 0 && height > 0) {
                canvasRenderer = WallpaperCanvasRenderer(width, height)
                if (shouldUseRealTimeCanvas()) {
                    drawRealTimeFrame(currentAnimProgress)
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            surfaceLockedByCanvas = false
            stopRealTimeAnimation()
            releaseMediaPlayer()
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            launcherXOffset = xOffset.coerceIn(0f, 1f)
            if (shouldUseRealTimeCanvas() && isVisible && !isAnimatingRealTime && prefs.getParallaxPercent() > 0) {
                drawRealTimeFrame(currentAnimProgress)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val keyguardLockedNow = km?.isKeyguardLocked == true
            if (keyguardLockedNow) {
                wasKeyguardLocked = true
            }

            if (visible) {

                val triggerMode = prefs.getTriggerMode()
                val shouldPlayFullAnim = if (triggerMode == 1) {
                    wasKeyguardLocked || keyguardLockedNow
                } else {
                    true
                }
                if (!keyguardLockedNow) {
                    wasKeyguardLocked = false
                }
                // If keyguard is still locked (fingerprint unlock in progress), poll every 16ms
                // so we start the animation the instant isKeyguardLocked flips to false,
                // instead of waiting for the (late) KeyguardLockedStateListener / ACTION_USER_PRESENT.
                // Continuous polling is already running; nothing extra needed here.
                schedulePlayAnimation(forceFullAnimation = shouldPlayFullAnim)
            } else {
                if (prefs.isDebounceVisibility()) {
                    handler.removeCallbacks(startAnimationRunnable)
                    handler.removeCallbacks(pauseAnimationRunnable)
                    handler.postDelayed(pauseAnimationRunnable, 50L)
                } else {
                    pauseAnimationRunnable.run()
                }
            }
        }

        private fun schedulePlayAnimation(forceFullAnimation: Boolean) {
            handler.removeCallbacks(startAnimationRunnable)
            handler.removeCallbacks(pauseAnimationRunnable)
            if (!forceFullAnimation) {
                if (shouldUseRealTimeCanvas()) {
                    stopRealTimeAnimation()
                    currentAnimProgress = 1f
                    drawRealTimeFrame(1f)
                } else {
                    mediaPlayer?.let { mp ->
                        try {
                            mp.seekTo((videoDurationMs - 30).coerceAtLeast(0))
                            mp.pause()
                        } catch (_: Throwable) {
                        }
                    }
                }
                return
            }

            val delayMs = prefs.getDelayTime()
            if (delayMs > 0L) {
                if (shouldUseRealTimeCanvas()) {
                    currentAnimProgress = 0f
                    drawRealTimeFrame(0f)
                }
                handler.postDelayed(startAnimationRunnable, delayMs)
            } else {
                // Zero-latency synchronous execution on the current frame tick
                startAnimationRunnable.run()
            }
        }

        private fun reloadEngineAssets() {
            if (prefs.getEngineMode() == 0) {
                releaseMediaPlayer()
                loadRealTimeBlurMipLevelsAsync()
            } else {
                stopRealTimeAnimation()
                if (surfaceLockedByCanvas) {
                    loadRealTimeBlurMipLevelsAsync()
                } else {
                    setupMediaPlayer()
                }
            }
        }

        private fun loadRealTimeBlurMipLevelsAsync() {
            val currentConfigTs = PrefsStore.getInstance().getLong("configTimestamp", 0L)
            // If levels are already built for this exact config, reuse them instantly
            // (avoids the multi-second re-blur delay when the surface is recreated on unlock).
            if (blurMipLevels.isNotEmpty() && lastLoadedConfigTimestamp == currentConfigTs) {
                handler.post {
                    if (shouldUseRealTimeCanvas()) {
                        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (km?.isKeyguardLocked == true) {
                            stageStartFrame()
                        } else if (isVisible) {
                            startRealTimeAnimation()
                        } else {
                            currentAnimProgress = 1f
                            drawRealTimeFrame(1f)
                        }
                    }
                }
                return
            }

            val savedPath = PrefsStore.getInstance().getString("path")
            val fallbackFile = File(baseDirPath ?: "", "selected_wallpaper.jpg")
            val targetPath = if (savedPath.isNotEmpty() && File(savedPath).exists()) {
                savedPath
            } else if (fallbackFile.exists()) {
                fallbackFile.absolutePath
            } else {
                return
            }

            val rotation = PrefsStore.getInstance().getInt("rotate", 0).coerceAtLeast(0)
            val panX = prefs.getPanX()
            val panY = prefs.getPanY()
            val scaleSize = prefs.getScaleSize()
            val parallaxExtra = (prefs.getParallaxPercent() / 100f) * 0.14f
            val startBlur = prefs.getStartBlur()
            val endBlur = prefs.getEndBlur()

            backgroundExecutor.execute {
                try {
                    val bounds = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        inJustDecodeBounds = true
                    }
                    ImageUtils.decodeBounds(this@ZoomyWallpaperService, targetPath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@execute

                    val screenW = AppUtils.getScreenWidth()
                    val screenH = AppUtils.getScreenHeight()
                    val ratio = bounds.outWidth / screenW
                    val sampleSize = if (bounds.outWidth <= screenW || ratio <= 3) 1 else ratio

                    var bmp = ImageUtils.decodeBitmap(this@ZoomyWallpaperService, targetPath, sampleSize) ?: return@execute
                    if (rotation != 0) {
                        bmp = ImageUtils.rotate(bmp, rotation, bmp.width / 2f, bmp.height / 2f) ?: bmp
                    }

                    val effectiveAspect = (screenW.toFloat() / screenH.toFloat()) * (1f + parallaxExtra)
                    val cropped = WallpaperVideoGenerator.cropToScreenAspect(bmp, effectiveAspect, panX, panY)

                    val maxW = (screenW * (1f + scaleSize + parallaxExtra)).roundToInt().coerceAtLeast(screenW)
                    val maxH = (screenH * (1f + scaleSize)).roundToInt().coerceAtLeast(screenH)
                    val base = if (cropped.width > maxW * 1.25f) {
                        Bitmap.createScaledBitmap(cropped, maxW, maxH, true)
                    } else {
                        cropped
                    }

                    // Build 10 perceptually-spaced blur mip-levels ((i / steps)^2.2):
                    // For startBlur=180, endBlur=0 -> radii [0, 1, 5, 11, 21, 35, 53, 76, 106, 180].
                    // This eliminates the dead zone of opaque blur (50..180) so unblurring and zooming
                    // are visibly synchronized from the very first frame, matching iOS.
                    val steps = 9
                    val levels = ArrayList<Bitmap>(steps + 1)
                    for (i in 0..steps) {
                        val linearFrac = i.toFloat() / steps.toFloat()
                        val perceptualFrac = linearFrac.pow(2.2f)
                        val r = (endBlur + (startBlur - endBlur) * perceptualFrac).roundToInt().coerceAtLeast(0)
                        val levelBmp = if (r <= 0) {
                            base
                        } else {
                            StackBlurEngine.fastBlurScaled(base, r)
                        }
                        levels.add(levelBmp)
                    }

                    blurMipLevels = levels
                    lastLoadedConfigTimestamp = currentConfigTs
                    handler.post {
                        if (shouldUseRealTimeCanvas()) {
                            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                            if (km?.isKeyguardLocked == true) {
                                stageStartFrame()
                            } else if (isVisible) {
                                startRealTimeAnimation()
                            } else {
                                currentAnimProgress = 1f
                                drawRealTimeFrame(1f)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }

        private fun startRealTimeAnimation() {
            if (blurMipLevels.isEmpty()) {
                loadRealTimeBlurMipLevelsAsync()
                return
            }
            Choreographer.getInstance().removeFrameCallback(this)
            animDurationMs = prefs.getDurationMillis()
            animStartTimeMs = SystemClock.uptimeMillis()
            currentAnimProgress = 0f
            isAnimatingRealTime = true
            // Draw Frame 0 synchronously right now so there is zero VSync wait latency
            drawRealTimeFrame(0f)
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun stopRealTimeAnimation() {
            isAnimatingRealTime = false
            Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimatingRealTime || !shouldUseRealTimeCanvas()) {
                isAnimatingRealTime = false
                return
            }
            val elapsed = (SystemClock.uptimeMillis() - animStartTimeMs).coerceAtLeast(0L)
            val rawT = (elapsed.toFloat() / animDurationMs.toFloat()).coerceIn(0f, 1f)
            currentAnimProgress = rawT
            drawRealTimeFrame(rawT)

            if (rawT < 1f && isVisible) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                isAnimatingRealTime = false
                currentAnimProgress = 1f
                drawRealTimeFrame(1f)
            }
        }

        private fun getOrCreateCurveInterpolator(): Interpolator {
            cachedCurveInterpolator?.let { return it }
            val created = when (prefs.getCurveType()) {
                0 -> CubicBezierInterpolator(0.16f, 1.0f, 0.30f, 1.0f)
                1 -> LinearOutSlowInInterpolator()
                2 -> FastOutSlowInInterpolator()
                else -> {
                    val p = prefs.getBezierControlPoints()
                    CubicBezierInterpolator(p.x1, p.y1, p.x2, p.y2)
                }
            }
            cachedCurveInterpolator = created
            return created
        }

        private fun drawRealTimeFrame(rawT: Float) {
            val levels = blurMipLevels
            if (levels.isEmpty()) return
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            val renderer = canvasRenderer ?: return

            val clampedT = rawT.coerceIn(0f, 1f)
            val curveInterp = getOrCreateCurveInterpolator()
            val curveProgress = curveInterp.getInterpolation(clampedT).coerceIn(0f, 1f)

            val totalInset = WallpaperVideoGenerator.computeZoomInsetFactor(
                zoomMode = prefs.getZoomMode(),
                scaleSize = prefs.getScaleSize(),
                curveProgress = curveProgress
            ).coerceIn(0f, 0.45f)

            // Blur decays on a faster power curve so unblurring is visible from the very first
            // frame (the cubic-bezier spring rises too slowly in its first 10% to show any change).
            // Zoom keeps using the iOS spring curve; both still start at t=0 and finish by blurDurationFrac.
            val blurDurationFrac = (prefs.getBlurTimePercent() / 100f).coerceIn(0f, 1f)
            val blurRemaining = if (blurDurationFrac <= 0.001f || clampedT >= blurDurationFrac || clampedT >= 0.995f) {
                0f
            } else {
                val bt = (clampedT / blurDurationFrac).coerceIn(0f, 1f)
                (1f - bt).coerceIn(0f, 1f)
            }

            val maxIdx = levels.size - 1
            val exactLevel = blurRemaining * maxIdx
            val lowIdx = exactLevel.toInt().coerceIn(0, maxIdx)
            val highIdx = (lowIdx + 1).coerceIn(0, maxIdx)
            val blendAlpha = ((exactLevel - lowIdx) * 255f).roundToInt().coerceIn(0, 255)

            val primaryBmp = levels[lowIdx]
            val secondaryBmp = if (highIdx != lowIdx && blendAlpha > 0) levels[highIdx] else null

            val parallaxShift = ((launcherXOffset - 0.5f) * 2f) * (prefs.getParallaxPercent() / 100f)
            val primaryRect = computeParallaxCropRect(primaryBmp.width, primaryBmp.height, totalInset, parallaxShift)
            val secondaryRect = secondaryBmp?.let {
                computeParallaxCropRect(it.width, it.height, totalInset, parallaxShift)
            }

            val isDark = WallpaperVideoGenerator.isSystemInDarkMode(this@ZoomyWallpaperService)
            val extraNightDim = if (isDark) prefs.getDarkModeExtraDimPercent() else 0
            val effectiveEndDim = (prefs.getEndDimPercent() + extraNightDim).coerceIn(0, 85)
            val effectiveStartDim = max(prefs.getStartDimPercent(), effectiveEndDim)
            val startDimAlpha = (effectiveStartDim / 100f) * 255f
            val endDimAlpha = (effectiveEndDim / 100f) * 255f
            val dimAlpha = (startDimAlpha + (endDimAlpha - startDimAlpha) * curveProgress).roundToInt()

            val (sat, bright) = WallpaperVideoGenerator.computeColorBloom(
                colorBloomPercent = prefs.getColorBloomPercent(),
                blurFractionRemaining = blurRemaining
            )

            val topScrimAlpha = ((prefs.getTopScrimPercent() / 100f) * 255f).roundToInt()
            val bottomScrimAlpha = ((prefs.getBottomScrimPercent() / 100f) * 255f).roundToInt()

            var canvas: android.graphics.Canvas? = null
            try {
                canvas = holder.lockHardwareCanvas()
                if (canvas != null) {
                    surfaceLockedByCanvas = true
                    renderer.drawFrame(
                        canvas = canvas,
                        primaryBitmap = primaryBmp,
                        primarySrcRect = primaryRect,
                        secondaryBitmap = secondaryBmp,
                        secondarySrcRect = secondaryRect,
                        secondaryAlpha = blendAlpha,
                        saturation = sat,
                        brightnessOffset = bright,
                        dimAlpha = dimAlpha,
                        topScrimAlpha = topScrimAlpha,
                        bottomScrimAlpha = bottomScrimAlpha
                    )
                }
            } catch (_: Throwable) {
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        private fun computeParallaxCropRect(
            bw: Int,
            bh: Int,
            insetFactor: Float,
            parallaxShift: Float
        ): Rect {
            val dx = (bw * insetFactor).roundToInt().coerceIn(0, bw / 3)
            val dy = (bh * insetFactor).roundToInt().coerceIn(0, bh / 3)
            val maxShiftPx = (dx * 0.85f).roundToInt()
            val shiftX = (parallaxShift * maxShiftPx).roundToInt().coerceIn(-dx, dx)
            return Rect(
                (dx + shiftX).coerceIn(0, bw - 1),
                dy,
                (bw - dx + shiftX).coerceIn(1, bw),
                bh - dy
            )
        }

        private fun setupMediaPlayer() {
            releaseMediaPlayer()
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            if (surfaceLockedByCanvas) {
                loadRealTimeBlurMipLevelsAsync()
                return
            }
            val videoFile = File(baseDirPath ?: "", "video/AnimVideo.mp4")
            if (!videoFile.exists()) {
                loadRealTimeBlurMipLevelsAsync()
                return
            }
            try {
                val mp = MediaPlayer()
                mp.setSurface(holder.surface)
                mp.setOnErrorListener(this)
                mp.setDataSource(videoFile.absolutePath)
                mp.prepare()
                videoDurationMs = mp.duration.coerceAtLeast(300)
                mp.start()
                mediaPlayer = mp
            } catch (e: Throwable) {
                e.printStackTrace()
                releaseMediaPlayer()
                loadRealTimeBlurMipLevelsAsync()
            }
        }

        private fun startVideoPlayback() {
            if (surfaceLockedByCanvas) {
                startRealTimeAnimation()
                return
            }
            val mp = mediaPlayer
            if (mp == null) {
                setupMediaPlayer()
                return
            }
            try {
                if (!prefs.isDebounceVisibility()) {
                    mp.seekTo(0)
                    mp.start()
                } else {
                    if (isPausedForVisibility) {
                        mp.seekTo(0)
                        mp.start()
                    }
                    isPausedForVisibility = false
                }
            } catch (_: Throwable) {
            }
        }

        private fun releaseMediaPlayer() {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                } catch (_: Throwable) {
                }
                try {
                    mp.release()
                } catch (_: Throwable) {
                }
            }
            mediaPlayer = null
        }

        override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
            return false
        }
    }
}
