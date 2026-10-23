package com.zoomy.wallpaper.encoder

import android.media.MediaCodecInfo
import android.media.MediaFormat

class AvcEncoderConfig(
    path: String,
    width: Int,
    height: Int,
    frameRate: Float,
    bitRate: Int
) : EncoderConfig(path, width, height, frameRate, bitRate) {

    override fun createMuxer(): FrameMuxer = Mp4FrameMuxer(path, frameRate)

    override fun createMediaFormat(): MediaFormat {
        return MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setFloat(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
        }
    }
}
