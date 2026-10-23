package com.zoomy.wallpaper.encoder

import android.media.MediaCodec
import android.media.MediaMuxer
import java.nio.ByteBuffer

class Mp4FrameMuxer(path: String, fps: Float) : FrameMuxer {
    private val frameUsec: Long = FrameEncoder.getFrameUsec(fps)
    private val muxer: MediaMuxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    override var isStarted: Boolean = false
        private set
    private var videoTrackIndex: Int = 0
    private var frameCount: Int = 0

    override fun start(encoder: FrameEncoder) {
        val outputFormat = encoder.codec?.outputFormat
            ?: throw IllegalStateException("Encoder output format is null")
        videoTrackIndex = muxer.addTrack(outputFormat)
        muxer.start()
        isStarted = true
    }

    override fun muxFrame(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        byteBuffer.position(bufferInfo.offset)
        byteBuffer.limit(bufferInfo.offset + bufferInfo.size)
        bufferInfo.presentationTimeUs = frameUsec * frameCount++
        muxer.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
    }

    override fun release() {
        try {
            if (isStarted) {
                muxer.stop()
            }
        } finally {
            muxer.release()
        }
    }
}
