package com.zoomy.wallpaper.encoder

import android.media.MediaCodec
import java.nio.ByteBuffer

interface FrameMuxer {
    val isStarted: Boolean
    fun start(encoder: FrameEncoder)
    fun muxFrame(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
    fun release()
}
