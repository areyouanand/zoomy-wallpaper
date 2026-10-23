package com.zoomy.wallpaper.encoder

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.view.animation.Interpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.zoomy.wallpaper.model.BezierControlPoints
import com.zoomy.wallpaper.util.AppUtils
import com.zoomy.wallpaper.util.CubicBezierInterpolator
import com.zoomy.wallpaper.util.FileUtils
import com.zoomy.wallpaper.util.ImageUtils
import com.zoomy.wallpaper.util.StackBlurEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class WallpaperRenderSpec(
    val imagePath: String,
    val rotationDegrees: Int,
    val panX: Float,
    val panY: Float,
    val curveType: Int,
    val customCurve: BezierControlPoints,
    val engineMode: Int = 0, // 0 = Real-Time 120Hz Engine, 1 = MP4 Video Engine
    val triggerMode: Int = 0, // 0 = Every Home Return, 1 = Device Unlock Only
    val delayTimeMs: Int = 0,
    val parallaxPercent: Int = 35,
    val depthZoomEnabled: Boolean = true,
    val encodeMode: Int = 1, // 0 = AVC, 1 = HEVC
    val fps: Float = 120f,   // 60f, 90f, 120f
    val addFps: Int = 60,
    val bitRateProgress: Int = 20,
    val bitRateMax: Int = 40,
    val zoomMode: Int = 0,   // 0 = Zoom Out->1x, 1 = 1x->Zoom In, 2 = Zoom Pulse
    val scaleSize: Float = 0.15f,
    val blurTimePercent: Int = 50,
    val startBlur: Int = 180,
    val endBlur: Int = 0,
    val startDimPercent: Int = 0,
    val endDimPercent: Int = 0,
    val topScrimPercent: Int = 0,
    val bottomScrimPercent: Int = 0,
    val colorBloomPercent: Int = 30,
    val darkModeExtraDimPercent: Int = 0
) {
    val totalFrames: Int
        get() = ((addFps + 30) * (fps / 60.0f)).roundToInt().coerceAtLeast(15)

    fun calculateBitRateBps(): Int {
        val baseUnit = 900000
        val halfMax = bitRateMax / 2
        var bitRate = if (bitRateProgress > halfMax) {
            (bitRateProgress - halfMax) * baseUnit
        } else {
            baseUnit / ((halfMax - bitRateProgress) + 1)
        }
        if (fps >= 90.0f) {
            bitRate = (bitRate * 0.85).toInt()
        }
        return bitRate
    }

    fun createInterpolator(): Interpolator {
        return when (curveType) {
            0 -> CubicBezierInterpolator(0.19f, 0.34f, 0.12f, 1.0f)
            1 -> LinearOutSlowInInterpolator()
            2 -> FastOutSlowInInterpolator()
            else -> CubicBezierInterpolator(
                customCurve.x1,
                customCurve.y1,
                customCurve.x2,
                customCurve.y2
            )
        }
    }
}

object WallpaperVideoGenerator {

    fun cropToScreenAspect(
        bitmap: Bitmap,
        screenAspect: Float,
        panX: Float,
        panY: Float
    ): Bitmap {
        val bw = bitmap.width
        val bh = bitmap.height
        val targetWidth = (bh * screenAspect).roundToInt()
        val targetHeight = (bw / screenAspect).roundToInt()

        return if (targetHeight < bh) {
            val maxExtraY = bh - targetHeight
            val centerY = maxExtraY / 2f
            val offsetY = (centerY + (panY.coerceIn(-1f, 1f) * centerY)).roundToInt().coerceIn(0, maxExtraY)
            ImageUtils.clip(bitmap, 0, offsetY, bw, targetHeight) ?: bitmap
        } else if (targetWidth < bw) {
            val maxExtraX = bw - targetWidth
            val centerX = maxExtraX / 2f
            val offsetX = (centerX + (panX.coerceIn(-1f, 1f) * centerX)).roundToInt().coerceIn(0, maxExtraX)
            ImageUtils.clip(bitmap, offsetX, 0, targetWidth, bh) ?: bitmap
        } else {
            bitmap
        }
    }

    fun isSystemInDarkMode(context: Context): Boolean {
        val nightFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightFlags == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Computes iOS "Color Wake" saturation and brightness offset for a given [blurFractionRemaining] (1f at start of blur -> 0f when unblurred).
     */
    fun computeColorBloom(
        colorBloomPercent: Int,
        blurFractionRemaining: Float
    ): Pair<Float, Float> {
        if (colorBloomPercent <= 0) return 1f to 0f
        val strength = (colorBloomPercent / 100f).coerceIn(0f, 1f)
        // At start of blur, saturation starts slightly muted (down to 0.65x) and blooms to 1.05x -> 1.0x
        val sat = 1f - (0.35f * strength * blurFractionRemaining)
        val bright = -14f * strength * blurFractionRemaining
        return sat to bright
    }

    suspend fun generateVideo(
        context: Context,
        spec: WallpaperRenderSpec,
        videoTempDir: File,
        videoFinalDir: File,
        onProgress: (frameIndex: Int, totalFrames: Int, elapsedMs: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        var encoder: FrameEncoder? = null
        try {
            val startTime = System.currentTimeMillis()
            FileUtils.createOrExistsDir(videoTempDir)
            FileUtils.createOrExistsDir(videoFinalDir)
            FileUtils.deleteFilesInDir(videoTempDir)

            val boundsOptions = BitmapFactory.Options().apply {
                inSampleSize = 1
                inJustDecodeBounds = true
            }
            ImageUtils.decodeBounds(context, spec.imagePath, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext Result.failure(IllegalStateException("Failed to read image dimensions"))
            }

            val screenWidth = AppUtils.getScreenWidth()
            val screenHeight = AppUtils.getScreenHeight()
            val widthRatio = boundsOptions.outWidth / screenWidth
            val sampleSize = if (boundsOptions.outWidth <= screenWidth || widthRatio <= 3) 1 else widthRatio

            var baseBitmap = ImageUtils.decodeBitmap(context, spec.imagePath, sampleSize)
                ?: return@withContext Result.failure(IllegalStateException("Decoded bitmap is null"))

            if (spec.rotationDegrees != 0) {
                baseBitmap = ImageUtils.rotate(
                    baseBitmap,
                    spec.rotationDegrees,
                    baseBitmap.width / 2f,
                    baseBitmap.height / 2f
                ) ?: baseBitmap
            }

            val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
            val croppedBitmap = cropToScreenAspect(baseBitmap, screenAspect, spec.panX, spec.panY)

            val maxWorkingWidth = (screenWidth * (1f + spec.scaleSize)).roundToInt().coerceAtLeast(screenWidth)
            val maxWorkingHeight = (screenHeight * (1f + spec.scaleSize)).roundToInt().coerceAtLeast(screenHeight)
            val workingBitmap = if (croppedBitmap.width > maxWorkingWidth * 1.2f) {
                Bitmap.createScaledBitmap(croppedBitmap, maxWorkingWidth, maxWorkingHeight, true)
            } else {
                croppedBitmap
            }

            val tempOutputFile = File(videoTempDir, "AnimVideo.mp4")
            val bitRateBps = spec.calculateBitRateBps()
            val encoderConfig = if (spec.encodeMode == 1) {
                HevcEncoderConfig(tempOutputFile.absolutePath, screenWidth, screenHeight, spec.fps, bitRateBps)
            } else {
                AvcEncoderConfig(tempOutputFile.absolutePath, screenWidth, screenHeight, spec.fps, bitRateBps)
            }

            encoder = FrameEncoder(encoderConfig).also { it.start() }

            val totalFrames = spec.totalFrames
            val maxBlurFrames = max(1f, (totalFrames - 2).toFloat())
            val blurFrameLimit = ((spec.blurTimePercent / 100f) * maxBlurFrames).roundToInt()
            val blurInterpolator = FastOutSlowInInterpolator()

            val restingBitmap: Bitmap = if (spec.endBlur > 0) {
                StackBlurEngine.fastBlurScaled(workingBitmap, spec.endBlur)
            } else {
                workingBitmap
            }

            val isDark = isSystemInDarkMode(context)
            val extraNightDim = if (isDark) spec.darkModeExtraDimPercent else 0
            val effectiveEndDimPercent = (spec.endDimPercent + extraNightDim).coerceIn(0, 85)
            val effectiveStartDimPercent = max(spec.startDimPercent, effectiveEndDimPercent)

            val topScrimAlpha = ((spec.topScrimPercent / 100f) * 255f).roundToInt().coerceIn(0, 255)
            val bottomScrimAlpha = ((spec.bottomScrimPercent / 100f) * 255f).roundToInt().coerceIn(0, 255)

            val batchSize = Runtime.getRuntime().availableProcessors().coerceIn(2, 3)
            var frameIndex = 0

            while (frameIndex < totalFrames) {
                ensureActive()
                val batchEnd = min(totalFrames, frameIndex + batchSize)
                val frameIndices = (frameIndex until batchEnd).toList()

                val batchFrames = coroutineScope {
                    frameIndices.map { idx ->
                        async(Dispatchers.Default) {
                            val radius = computeFrameBlurRadius(
                                frameIndex = idx,
                                blurFrameLimit = blurFrameLimit,
                                startBlur = spec.startBlur,
                                endBlur = spec.endBlur,
                                blurInterpolator = blurInterpolator
                            )
                            val bmp = when {
                                radius <= 0 -> workingBitmap
                                radius == spec.endBlur -> restingBitmap
                                else -> StackBlurEngine.fastBlurScaled(workingBitmap, radius)
                            }
                            Triple(idx, radius, bmp)
                        }
                    }.awaitAll()
                }

                val curveInterpolator = spec.createInterpolator()
                for ((idx, radius, frameBmp) in batchFrames) {
                    ensureActive()
                    val t = idx.toFloat() / totalFrames.toFloat()
                    val curveProgress = curveInterpolator.getInterpolation(t).coerceIn(0f, 1f)

                    val insetFactor = computeZoomInsetFactor(
                        zoomMode = spec.zoomMode,
                        scaleSize = spec.scaleSize,
                        curveProgress = curveProgress
                    )

                    val bw = frameBmp.width
                    val bh = frameBmp.height
                    val dx = (bw * insetFactor).roundToInt().coerceIn(0, bw / 3)
                    val dy = (bh * insetFactor).roundToInt().coerceIn(0, bh / 3)
                    val srcRect = Rect(dx, dy, bw - dx, bh - dy)

                    val startDimAlpha = (effectiveStartDimPercent / 100f) * 255f
                    val endDimAlpha = (effectiveEndDimPercent / 100f) * 255f
                    val dimAlpha = (startDimAlpha + (endDimAlpha - startDimAlpha) * curveProgress).roundToInt()

                    val blurRange = (spec.startBlur - spec.endBlur).coerceAtLeast(1).toFloat()
                    val blurRem = ((radius - spec.endBlur).toFloat() / blurRange).coerceIn(0f, 1f)
                    val (sat, bright) = computeColorBloom(spec.colorBloomPercent, blurRem)

                    encoder.encodeFrameTransformed(
                        bitmap = frameBmp,
                        srcRect = srcRect,
                        saturation = sat,
                        brightnessOffset = bright,
                        dimAlpha = dimAlpha,
                        topScrimAlpha = topScrimAlpha,
                        bottomScrimAlpha = bottomScrimAlpha
                    )

                    if (frameBmp !== workingBitmap && frameBmp !== restingBitmap && !frameBmp.isRecycled) {
                        frameBmp.recycle()
                    }

                    onProgress(idx + 1, totalFrames, System.currentTimeMillis() - startTime)
                }

                frameIndex = batchEnd
            }

            encoder.release()
            encoder = null

            FileUtils.moveDir(videoTempDir, videoFinalDir)
            val finalVideoFile = File(videoFinalDir, "AnimVideo.mp4")
            Result.success(finalVideoFile)
        } catch (e: Throwable) {
            e.printStackTrace()
            try {
                encoder?.release()
            } catch (_: Throwable) {
            }
            Result.failure(e)
        }
    }

    fun computeFrameBlurRadius(
        frameIndex: Int,
        blurFrameLimit: Int,
        startBlur: Int,
        endBlur: Int,
        blurInterpolator: Interpolator
    ): Int {
        if (blurFrameLimit <= 0 || frameIndex >= blurFrameLimit) {
            return endBlur
        }
        val t = (frameIndex.toFloat() / blurFrameLimit.toFloat()).coerceIn(0f, 1f)
        val p = blurInterpolator.getInterpolation(t)
        return (startBlur + (endBlur - startBlur) * p).roundToInt().coerceAtLeast(0)
    }

    fun computeZoomInsetFactor(
        zoomMode: Int,
        scaleSize: Float,
        curveProgress: Float
    ): Float {
        return when (zoomMode) {
            0 -> scaleSize * (1f - curveProgress)
            1 -> scaleSize * curveProgress
            2 -> scaleSize * sin(curveProgress * PI).toFloat()
            else -> scaleSize * (1f - curveProgress)
        }.coerceIn(0f, 0.49f)
    }
}
