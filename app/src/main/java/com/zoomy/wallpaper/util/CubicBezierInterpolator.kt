package com.zoomy.wallpaper.util

import android.graphics.PointF
import android.view.animation.Interpolator

class CubicBezierInterpolator(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float
) : Interpolator {

    private var lastIndex = 0
    private var lastInput = -1f
    private val controlPoint1 = PointF(x1, y1)
    private val controlPoint2 = PointF(x2, y2)

    override fun getInterpolation(input: Float): Float {
        // Reset the cached search index whenever input goes backwards (e.g. a new
        // unlock animation restarts at t = 0) so we never return a stale settled value.
        if (input < lastInput) {
            lastIndex = 0
        }
        lastInput = input

        var t = input
        for (i in lastIndex until 4096) {
            t = i / 4096.0f
            if (cubicCurves(t.toDouble(), 0.0, controlPoint1.x.toDouble(), controlPoint2.x.toDouble(), 1.0) >= input) {
                lastIndex = i
                break
            }
        }
        // If the loop never broke (input == 1.0), pin t to the end.
        if (lastIndex == 4095 && cubicCurves(t.toDouble(), 0.0, controlPoint1.x.toDouble(), controlPoint2.x.toDouble(), 1.0) < input) {
            t = 1f
            lastIndex = 4096
        }
        var value = cubicCurves(t.toDouble(), 0.0, controlPoint1.y.toDouble(), controlPoint2.y.toDouble(), 1.0)
        if (value > 0.999) {
            value = 1.0
        }
        if (input <= 0f) {
            lastIndex = 0
            lastInput = 0f
        }
        return value.toFloat()
    }

    companion object {
        fun cubicCurves(t: Double, value0: Double, value1: Double, value2: Double, value3: Double): Double {
            val u = 1.0 - t
            val tt = t * t
            val uu = u * u
            val uuu = uu * u
            val ttt = tt * t
            return uuu * value0 + 3.0 * uu * t * value1 + 3.0 * u * tt * value2 + ttt * value3
        }
    }
}
