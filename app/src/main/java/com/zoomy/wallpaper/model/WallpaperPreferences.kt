package com.zoomy.wallpaper.model

import android.content.Context
import android.view.WindowManager
import com.zoomy.wallpaper.util.AppUtils
import com.zoomy.wallpaper.util.PrefsStore
import kotlin.math.roundToInt

class WallpaperPreferences private constructor() {

    fun setBezierControlPoints(params: BezierControlPoints) {
        PrefsStore.getInstance().put("bezier.x1", params.x1)
        PrefsStore.getInstance().put("bezier.y1", params.y1)
        PrefsStore.getInstance().put("bezier.x2", params.x2)
        PrefsStore.getInstance().put("bezier.y2", params.y2)
    }

    fun getBezierControlPoints(): BezierControlPoints {
        return BezierControlPoints(
            x1 = PrefsStore.getInstance().getFloat("bezier.x1", 0.19f),
            y1 = PrefsStore.getInstance().getFloat("bezier.y1", 0.34f),
            x2 = PrefsStore.getInstance().getFloat("bezier.x2", 0.12f),
            y2 = PrefsStore.getInstance().getFloat("bezier.y2", 1.0f)
        )
    }

    fun getAddFps(): Int = PrefsStore.getInstance().getInt("addFps", 60)

    fun setAddFps(value: Int) = PrefsStore.getInstance().put("addFps", value)

    fun getTotalFrames(): Int {
        val base60Frames = getAddFps() + 30
        val fpsMultiplier = getFps() / 60.0f
        return (base60Frames * fpsMultiplier).roundToInt().coerceAtLeast(15)
    }

    fun getDurationMillis(): Long {
        return ((getTotalFrames() / getFps()) * 1000f).toLong().coerceIn(250L, 5000L)
    }

    /**
     * Group 1: Start delay in milliseconds (0..500 ms)
     */
    fun getDelayTime(): Long = PrefsStore.getInstance().getInt("delayTime", 0).coerceIn(0, 500).toLong()

    fun setDelayTime(delayMs: Int) = PrefsStore.getInstance().put("delayTime", delayMs.coerceIn(0, 500))

    /**
     * Group 1: Trigger mode:
     * 0 = Every Home Return (unlock + exiting apps)
     * 1 = Device Unlock Only (only when unlocking from lockscreen)
     */
    fun getTriggerMode(): Int = PrefsStore.getInstance().getInt("triggerMode", 0)

    fun setTriggerMode(mode: Int) = PrefsStore.getInstance().put("triggerMode", mode)

    /**
     * Group 3: Engine Mode:
     * 0 = Real-Time 120Hz Canvas Engine (Instant Apply + Parallax + Launcher Depth)
     * 1 = MP4 Video Engine (Hardware MediaCodec MP4)
     */
    fun getEngineMode(): Int = PrefsStore.getInstance().getInt("engineMode", 0)

    fun setEngineMode(mode: Int) = PrefsStore.getInstance().put("engineMode", mode)

    /**
     * Group 3: Home Screen Swipe Parallax (0..100%)
     */
    fun getParallaxPercent(): Int = PrefsStore.getInstance().getInt("parallaxPercent", 35).coerceIn(0, 100)

    fun setParallaxPercent(percent: Int) = PrefsStore.getInstance().put("parallaxPercent", percent.coerceIn(0, 100))

    /**
     * Group 3: Android 11+ Launcher Depth Zoom (onZoomChanged when pulling shade / app drawer / Recents)
     */
    fun isDepthZoomEnabled(): Boolean = PrefsStore.getInstance().getBoolean("depthZoomEnabled", true)

    fun setDepthZoomEnabled(enabled: Boolean) = PrefsStore.getInstance().put("depthZoomEnabled", enabled)

    fun getFps(): Float {
        val defaultFps = detectMaxDisplayFps()
        return PrefsStore.getInstance().getFloat("fps", defaultFps)
    }

    fun setFps(fps: Float) = PrefsStore.getInstance().put("fps", fps)

    fun getScaleSize(): Float = PrefsStore.getInstance().getFloat("scaleSize", 0.15f)

    fun setScaleSize(scale: Float) = PrefsStore.getInstance().put("scaleSize", scale)

    fun getZoomMode(): Int = PrefsStore.getInstance().getInt("zoomMode", 0)

    fun setZoomMode(mode: Int) = PrefsStore.getInstance().put("zoomMode", mode)

    fun getCurveType(): Int = PrefsStore.getInstance().getInt("curveType", 0)

    fun setCurveType(type: Int) = PrefsStore.getInstance().put("curveType", type)

    fun getEncodeMode(): Int = PrefsStore.getInstance().getInt("encodeMode", 1)

    fun setEncodeMode(mode: Int) = PrefsStore.getInstance().put("encodeMode", mode)

    fun getBlurTimePercent(): Int = PrefsStore.getInstance().getInt("blurTime", 50)

    fun setBlurTimePercent(value: Int) = PrefsStore.getInstance().put("blurTime", value)

    fun getStartBlur(): Int = PrefsStore.getInstance().getInt("startBlur", 180)

    fun setStartBlur(value: Int) = PrefsStore.getInstance().put("startBlur", value)

    fun getEndBlur(): Int = PrefsStore.getInstance().getInt("endBlur", 0)

    fun setEndBlur(value: Int) = PrefsStore.getInstance().put("endBlur", value)

    fun getStartDimPercent(): Int = PrefsStore.getInstance().getInt("startDim", 0)

    fun setStartDimPercent(value: Int) = PrefsStore.getInstance().put("startDim", value)

    fun getEndDimPercent(): Int = PrefsStore.getInstance().getInt("endDim", 0)

    fun setEndDimPercent(value: Int) = PrefsStore.getInstance().put("endDim", value)

    /**
     * Group 2: Top Status Bar & Bottom Dock Gradient Scrims (0..80%)
     */
    fun getTopScrimPercent(): Int = PrefsStore.getInstance().getInt("topScrim", 0).coerceIn(0, 80)

    fun setTopScrimPercent(value: Int) = PrefsStore.getInstance().put("topScrim", value.coerceIn(0, 80))

    fun getBottomScrimPercent(): Int = PrefsStore.getInstance().getInt("bottomScrim", 0).coerceIn(0, 80)

    fun setBottomScrimPercent(value: Int) = PrefsStore.getInstance().put("bottomScrim", value.coerceIn(0, 80))

    /**
     * Group 2: iOS "Color Wake" (Saturation & Brightness Bloom during unblur, 0..100%)
     */
    fun getColorBloomPercent(): Int = PrefsStore.getInstance().getInt("colorBloom", 30).coerceIn(0, 100)

    fun setColorBloomPercent(value: Int) = PrefsStore.getInstance().put("colorBloom", value.coerceIn(0, 100))

    /**
     * Group 2: Auto-Dim in System Dark Mode (0..60% extra dim when phone is in Dark Mode)
     */
    fun getDarkModeExtraDimPercent(): Int = PrefsStore.getInstance().getInt("darkModeExtraDim", 0).coerceIn(0, 60)

    fun setDarkModeExtraDimPercent(value: Int) = PrefsStore.getInstance().put("darkModeExtraDim", value.coerceIn(0, 60))

    fun getBitRateProgress(): Int = PrefsStore.getInstance().getInt("bitRate", 20)

    fun setBitRateProgress(value: Int) = PrefsStore.getInstance().put("bitRate", value)

    fun getPanX(): Float = PrefsStore.getInstance().getFloat("panX", 0f).coerceIn(-1f, 1f)

    fun setPanX(value: Float) = PrefsStore.getInstance().put("panX", value.coerceIn(-1f, 1f))

    fun getPanY(): Float = PrefsStore.getInstance().getFloat("panY", 0f).coerceIn(-1f, 1f)

    fun setPanY(value: Float) = PrefsStore.getInstance().put("panY", value.coerceIn(-1f, 1f))

    fun isRewindOnHide(): Boolean = PrefsStore.getInstance().getBoolean("rewindOnHide", false)

    fun setRewindOnHide(value: Boolean) = PrefsStore.getInstance().put("rewindOnHide", value)

    fun isDebounceVisibility(): Boolean = PrefsStore.getInstance().getBoolean("debounceVisibility", false)

    fun setDebounceVisibility(value: Boolean) = PrefsStore.getInstance().put("debounceVisibility", value)

    fun getThemeMode(): Int = PrefsStore.getInstance().getInt("themeMode", 0)

    fun setThemeMode(mode: Int) = PrefsStore.getInstance().put("themeMode", mode)

    fun notifyConfigUpdated() {
        PrefsStore.getInstance().putLong("configTimestamp", System.currentTimeMillis())
    }

    fun resetAllToDefaults() {
        setCurveType(0)
        setBezierControlPoints(BezierControlPoints(0.19f, 0.34f, 0.12f, 1.0f))
        setFps(detectMaxDisplayFps())
        setEncodeMode(1)
        setEngineMode(0)
        setTriggerMode(0)
        setDelayTime(0)
        setParallaxPercent(35)
        setDepthZoomEnabled(true)
        setZoomMode(0)
        setAddFps(60)
        setScaleSize(0.15f)
        setBlurTimePercent(50)
        setStartBlur(180)
        setEndBlur(0)
        setStartDimPercent(0)
        setEndDimPercent(0)
        setTopScrimPercent(0)
        setBottomScrimPercent(0)
        setColorBloomPercent(30)
        setDarkModeExtraDimPercent(0)
        setBitRateProgress(20)
        setPanX(0f)
        setPanY(0f)
        notifyConfigUpdated()
    }

    @Suppress("DEPRECATION")
    fun detectMaxDisplayFps(): Float {
        return try {
            val wm = AppUtils.app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val maxRate = wm?.defaultDisplay?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f
            when {
                maxRate >= 115f -> 120f
                maxRate >= 85f -> 90f
                else -> 60f
            }
        } catch (_: Exception) {
            60f
        }
    }

    companion object {
        @Volatile
        private var instance: WallpaperPreferences? = null

        @JvmStatic
        fun getInstance(): WallpaperPreferences {
            return instance ?: synchronized(this) {
                instance ?: WallpaperPreferences().also { instance = it }
            }
        }
    }
}
