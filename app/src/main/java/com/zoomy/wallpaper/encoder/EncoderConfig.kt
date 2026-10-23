package com.zoomy.wallpaper.encoder

import android.media.MediaFormat

abstract class EncoderConfig(
    val path: String,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val bitRate: Int
) {
    abstract fun createMuxer(): FrameMuxer
    abstract fun createMediaFormat(): MediaFormat
}
