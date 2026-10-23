package com.zoomy.wallpaper.util

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BlurWorkspace {
    var pix = IntArray(0)
    var vmin = IntArray(0)
    var stack = IntArray(0)

    fun ensureCapacity(w: Int, h: Int, div: Int) {
        val neededPix = w * h
        if (pix.size < neededPix) {
            pix = IntArray(neededPix)
        }
        val neededVmin = max(w, h)
        if (vmin.size < neededVmin) {
            vmin = IntArray(neededVmin)
        }
        val neededStack = div * 3
        if (stack.size < neededStack) {
            stack = IntArray(neededStack)
        }
    }
}

object StackBlurEngine {

    private val STACKBLUR_MUL = intArrayOf(
        512, 512, 456, 512, 328, 456, 335, 512, 405, 328, 271, 456, 388, 335, 292, 512,
        454, 405, 364, 328, 298, 271, 496, 456, 420, 388, 360, 335, 312, 292, 273, 512,
        482, 454, 428, 405, 383, 364, 345, 328, 312, 298, 284, 271, 259, 496, 475, 456,
        437, 420, 404, 388, 374, 360, 347, 335, 323, 312, 302, 292, 282, 273, 265, 512,
        497, 482, 468, 454, 441, 428, 417, 405, 394, 383, 373, 364, 354, 345, 337, 328,
        320, 312, 305, 298, 291, 284, 278, 271, 265, 259, 507, 496, 485, 475, 465, 456,
        446, 437, 428, 420, 412, 404, 396, 388, 381, 374, 367, 360, 354, 347, 341, 335,
        329, 323, 318, 312, 307, 302, 297, 292, 287, 282, 278, 273, 269, 265, 261, 512,
        505, 497, 489, 482, 475, 468, 461, 454, 447, 441, 435, 428, 422, 417, 411, 405,
        399, 394, 389, 383, 378, 373, 368, 364, 359, 354, 350, 345, 341, 337, 332, 328,
        324, 320, 316, 312, 309, 305, 301, 298, 294, 291, 287, 284, 281, 278, 274, 271,
        268, 265, 262, 259, 257, 507, 501, 496, 491, 485, 480, 475, 470, 465, 460, 456,
        451, 446, 442, 437, 433, 428, 424, 420, 416, 412, 408, 404, 400, 396, 392, 388,
        385, 381, 377, 374, 370, 367, 363, 360, 357, 354, 350, 347, 344, 341, 338, 335,
        332, 329, 326, 323, 320, 318, 315, 312, 310, 307, 304, 302, 299, 297, 294, 292,
        289, 287, 285, 282, 280, 278, 275, 273, 271, 269, 267, 265, 263, 261, 259
    )

    private val STACKBLUR_SHR = intArrayOf(
        9, 11, 12, 13, 13, 14, 14, 15, 15, 15, 15, 16, 16, 16, 16, 17,
        17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 18, 18, 18, 19,
        19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 20, 20, 20,
        20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 21,
        21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21,
        21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 22, 22, 22, 22, 22, 22,
        22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
        22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 23,
        23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23,
        23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23,
        23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23,
        23, 23, 23, 23, 23, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
        24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
        24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
        24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
        24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24
    )

    private val threadLocalWorkspace = ThreadLocal.withInitial { BlurWorkspace() }

    @JvmStatic
    fun fastBlurScaled(
        bitmap: Bitmap,
        radius: Int,
        workspace: BlurWorkspace = threadLocalWorkspace.get()!!
    ): Bitmap {
        if (radius <= 0) return bitmap
        val downscale = when {
            radius >= 36 -> 3
            radius >= 4 -> 2
            else -> 1
        }
        return if (downscale == 1) {
            val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            fastBlurInPlace(out, radius, workspace)
            out
        } else {
            val scaledW = max(1, bitmap.width / downscale)
            val scaledH = max(1, bitmap.height / downscale)
            val scaledRadius = max(1, (radius.toFloat() / downscale).roundToInt())
            val small = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            fastBlurInPlace(small, scaledRadius, workspace)
            small
        }
    }

    @JvmStatic
    fun fastBlurInPlace(
        bitmap: Bitmap,
        radius: Int,
        workspace: BlurWorkspace = threadLocalWorkspace.get()!!
    ) {
        if (radius < 1) return
        val w = bitmap.width
        val h = bitmap.height
        val r = radius.coerceIn(1, 254)

        val wm = w - 1
        val hm = h - 1
        val div = r + r + 1
        val mulSum = STACKBLUR_MUL[r]
        val shrSum = STACKBLUR_SHR[r]

        workspace.ensureCapacity(w, h, div)
        val pix = workspace.pix
        val vmin = workspace.vmin
        val stack = workspace.stack

        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val r1 = r + 1
        var yw = 0
        var yi = 0

        // Pass 1: Horizontal blur (stores blurred R,G,B directly back into pix[yi])
        for (y in 0 until h) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            var rInSum = 0
            var gInSum = 0
            var bInSum = 0
            var rOutSum = 0
            var gOutSum = 0
            var bOutSum = 0

            for (i in -r..r) {
                val p = pix[yi + i.coerceIn(0, wm)]
                val sirIdx = (i + r) * 3
                val pr = (p shr 16) and 0xff
                val pg = (p shr 8) and 0xff
                val pb = p and 0xff
                stack[sirIdx] = pr
                stack[sirIdx + 1] = pg
                stack[sirIdx + 2] = pb
                val rbs = r1 - abs(i)
                rSum += pr * rbs
                gSum += pg * rbs
                bSum += pb * rbs
                if (i > 0) {
                    rInSum += pr
                    gInSum += pg
                    bInSum += pb
                } else {
                    rOutSum += pr
                    gOutSum += pg
                    bOutSum += pb
                }
            }
            var stackPointer = r

            for (x in 0 until w) {
                if (y == 0) {
                    vmin[x] = min(x + r + 1, wm)
                }
                val nextP = pix[yw + vmin[x]]

                val rOut = (rSum * mulSum) ushr shrSum
                val gOut = (gSum * mulSum) ushr shrSum
                val bOut = (bSum * mulSum) ushr shrSum
                pix[yi] = (pix[yi] and -0x1000000) or (rOut shl 16) or (gOut shl 8) or bOut

                rSum -= rOutSum
                gSum -= gOutSum
                bSum -= bOutSum

                val stackStart = (stackPointer - r + div) % div
                val sirIdx = stackStart * 3

                rOutSum -= stack[sirIdx]
                gOutSum -= stack[sirIdx + 1]
                bOutSum -= stack[sirIdx + 2]

                val pr = (nextP shr 16) and 0xff
                val pg = (nextP shr 8) and 0xff
                val pb = nextP and 0xff

                stack[sirIdx] = pr
                stack[sirIdx + 1] = pg
                stack[sirIdx + 2] = pb

                rInSum += pr
                gInSum += pg
                bInSum += pb

                rSum += rInSum
                gSum += gInSum
                bSum += bInSum

                stackPointer = (stackPointer + 1) % div
                val sirNextIdx = stackPointer * 3
                val prNext = stack[sirNextIdx]
                val pgNext = stack[sirNextIdx + 1]
                val pbNext = stack[sirNextIdx + 2]

                rOutSum += prNext
                gOutSum += pgNext
                bOutSum += pbNext

                rInSum -= prNext
                gInSum -= pgNext
                bInSum -= pbNext

                yi++
            }
            yw += w
        }

        // Pass 2: Vertical blur (reads horizontal-blurred R,G,B from pix and writes final pixel back)
        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            var rInSum = 0
            var gInSum = 0
            var bInSum = 0
            var rOutSum = 0
            var gOutSum = 0
            var bOutSum = 0

            var yp = -r * w
            for (i in -r..r) {
                val yi2 = max(0, yp) + x
                val sirIdx = (i + r) * 3
                val p = pix[yi2]
                val pr = (p shr 16) and 0xff
                val pg = (p shr 8) and 0xff
                val pb = p and 0xff
                stack[sirIdx] = pr
                stack[sirIdx + 1] = pg
                stack[sirIdx + 2] = pb
                val rbs = r1 - abs(i)
                rSum += pr * rbs
                gSum += pg * rbs
                bSum += pb * rbs
                if (i > 0) {
                    rInSum += pr
                    gInSum += pg
                    bInSum += pb
                } else {
                    rOutSum += pr
                    gOutSum += pg
                    bOutSum += pb
                }
                if (i < hm) {
                    yp += w
                }
            }
            var yi2 = x
            var stackPointer = r
            for (y in 0 until h) {
                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * w
                }
                val nextP = pix[x + vmin[y]]

                pix[yi2] = (pix[yi2] and -0x1000000) or
                    (((rSum * mulSum) ushr shrSum) shl 16) or
                    (((gSum * mulSum) ushr shrSum) shl 8) or
                    ((bSum * mulSum) ushr shrSum)

                rSum -= rOutSum
                gSum -= gOutSum
                bSum -= bOutSum

                val stackStart = (stackPointer - r + div) % div
                val sirIdx = stackStart * 3

                rOutSum -= stack[sirIdx]
                gOutSum -= stack[sirIdx + 1]
                bOutSum -= stack[sirIdx + 2]

                val pr = (nextP shr 16) and 0xff
                val pg = (nextP shr 8) and 0xff
                val pb = nextP and 0xff

                stack[sirIdx] = pr
                stack[sirIdx + 1] = pg
                stack[sirIdx + 2] = pb

                rInSum += pr
                gInSum += pg
                bInSum += pb

                rSum += rInSum
                gSum += gInSum
                bSum += bInSum

                stackPointer = (stackPointer + 1) % div
                val sirNextIdx = stackPointer * 3
                val prNext = stack[sirNextIdx]
                val pgNext = stack[sirNextIdx + 1]
                val pbNext = stack[sirNextIdx + 2]

                rOutSum += prNext
                gOutSum += pgNext
                bOutSum += pbNext

                rInSum -= prNext
                gInSum -= pgNext
                bInSum -= pbNext

                yi2 += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }
}
