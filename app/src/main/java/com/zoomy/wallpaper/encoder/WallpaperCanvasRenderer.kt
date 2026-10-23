package com.zoomy.wallpaper.encoder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import kotlin.math.abs

class WallpaperCanvasRenderer(
    private val width: Int,
    private val height: Int
) {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val destRect = Rect(0, 0, width, height)

    private val satMatrix = ColorMatrix()
    private val scaleMatrix = ColorMatrix()
    private var lastSat = 1f
    private var lastBright = 0f

    private var cachedTopScrimAlpha = -1
    private var topScrimShader: LinearGradient? = null

    private var cachedBottomScrimAlpha = -1
    private var bottomScrimShader: LinearGradient? = null

    private fun updateColorFilter(saturation: Float, brightnessOffset: Float) {
        if (abs(saturation - 1f) < 0.01f && abs(brightnessOffset) < 0.5f) {
            bitmapPaint.colorFilter = null
            overlayPaint.colorFilter = null
            lastSat = 1f
            lastBright = 0f
            return
        }
        if (abs(saturation - lastSat) > 0.01f || abs(brightnessOffset - lastBright) > 0.5f) {
            lastSat = saturation
            lastBright = brightnessOffset
            satMatrix.setSaturation(saturation)
            if (abs(brightnessOffset) >= 0.5f) {
                scaleMatrix.set(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, brightnessOffset,
                        0f, 1f, 0f, 0f, brightnessOffset,
                        0f, 0f, 1f, 0f, brightnessOffset,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                satMatrix.postConcat(scaleMatrix)
            }
            val filter = ColorMatrixColorFilter(satMatrix)
            bitmapPaint.colorFilter = filter
            overlayPaint.colorFilter = filter
        }
    }

    fun drawFrame(
        canvas: Canvas,
        primaryBitmap: Bitmap,
        primarySrcRect: Rect,
        secondaryBitmap: Bitmap? = null,
        secondarySrcRect: Rect? = null,
        secondaryAlpha: Int = 0,
        saturation: Float = 1f,
        brightnessOffset: Float = 0f,
        dimAlpha: Int = 0,
        topScrimAlpha: Int = 0,
        bottomScrimAlpha: Int = 0
    ) {
        updateColorFilter(saturation, brightnessOffset)
        bitmapPaint.alpha = 255
        canvas.drawBitmap(primaryBitmap, primarySrcRect, destRect, bitmapPaint)

        if (secondaryBitmap != null && secondarySrcRect != null && secondaryAlpha > 0) {
            overlayPaint.alpha = secondaryAlpha.coerceIn(0, 255)
            canvas.drawBitmap(secondaryBitmap, secondarySrcRect, destRect, overlayPaint)
        }

        if (dimAlpha > 0) {
            canvas.drawColor(Color.argb(dimAlpha.coerceIn(0, 255), 0, 0, 0))
        }

        if (topScrimAlpha > 0) {
            val alpha = topScrimAlpha.coerceIn(0, 255)
            val topHeight = height * 0.18f
            if (alpha != cachedTopScrimAlpha || topScrimShader == null) {
                cachedTopScrimAlpha = alpha
                topScrimShader = LinearGradient(
                    0f, 0f, 0f, topHeight,
                    intArrayOf(
                        Color.argb(alpha, 0, 0, 0),
                        Color.argb((alpha * 0.45f).toInt(), 0, 0, 0),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            scrimPaint.shader = topScrimShader
            canvas.drawRect(0f, 0f, width.toFloat(), topHeight, scrimPaint)
        }

        if (bottomScrimAlpha > 0) {
            val alpha = bottomScrimAlpha.coerceIn(0, 255)
            val bottomHeight = height * 0.24f
            val topY = height - bottomHeight
            if (alpha != cachedBottomScrimAlpha || bottomScrimShader == null) {
                cachedBottomScrimAlpha = alpha
                bottomScrimShader = LinearGradient(
                    0f, height.toFloat(), 0f, topY,
                    intArrayOf(
                        Color.argb(alpha, 0, 0, 0),
                        Color.argb((alpha * 0.5f).toInt(), 0, 0, 0),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            scrimPaint.shader = bottomScrimShader
            canvas.drawRect(0f, topY, width.toFloat(), height.toFloat(), scrimPaint)
        }
    }
}
