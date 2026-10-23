package com.zoomy.wallpaper.encoder

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface

class FrameEncoder(private val config: EncoderConfig) {
    private var bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
    var codec: MediaCodec? = null
        private set
    private var inputSurface: Surface? = null
    private var muxer: FrameMuxer? = null

    private val renderer = WallpaperCanvasRenderer(config.width, config.height)

    fun start() {
        bufferInfo = MediaCodec.BufferInfo()
        val format = config.createMediaFormat()
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalArgumentException("Missing MIME type in MediaFormat")
        val mediaCodec = MediaCodec.createEncoderByType(mime)
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = mediaCodec.createInputSurface()
        muxer = config.createMuxer()
        mediaCodec.start()
        codec = mediaCodec
        drainEncoder(false)
    }

    fun encodeFrameTransformed(
        bitmap: Bitmap,
        srcRect: Rect,
        saturation: Float = 1f,
        brightnessOffset: Float = 0f,
        dimAlpha: Int = 0,
        topScrimAlpha: Int = 0,
        bottomScrimAlpha: Int = 0
    ) {
        val surface = inputSurface ?: return
        val canvas = surface.lockHardwareCanvas()
        renderer.drawFrame(
            canvas = canvas,
            primaryBitmap = bitmap,
            primarySrcRect = srcRect,
            saturation = saturation,
            brightnessOffset = brightnessOffset,
            dimAlpha = dimAlpha,
            topScrimAlpha = topScrimAlpha,
            bottomScrimAlpha = bottomScrimAlpha
        )
        surface.unlockCanvasAndPost(canvas)
        drainEncoder(false)
    }

    @Suppress("DEPRECATION")
    private fun drainEncoder(endOfStream: Boolean) {
        val mediaCodec = codec ?: return
        val frameMuxer = muxer ?: return
        if (endOfStream) {
            mediaCodec.signalEndOfInputStream()
        }
        var outputBuffers = mediaCodec.outputBuffers
        while (true) {
            val encoderStatus = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000L)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mediaCodec.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!frameMuxer.isStarted) {
                    frameMuxer.start(this)
                } else {
                    throw RuntimeException("format changed twice")
                }
            } else if (encoderStatus >= 0) {
                val encodedData = outputBuffers[encoderStatus]
                    ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0) {
                    if (frameMuxer.isStarted) {
                        frameMuxer.muxFrame(encodedData, bufferInfo)
                    } else {
                        throw RuntimeException("muxer hasn't started")
                    }
                }
                mediaCodec.releaseOutputBuffer(encoderStatus, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    return
                }
            }
        }
    }

    fun release() {
        codec?.let { mediaCodec ->
            drainEncoder(true)
            mediaCodec.stop()
            mediaCodec.release()
        }
        codec = null
        inputSurface?.release()
        inputSurface = null
        muxer?.release()
        muxer = null
    }

    companion object {
        fun getFrameUsec(fps: Float): Long = (1000000.0f / fps).toLong()
    }
}
