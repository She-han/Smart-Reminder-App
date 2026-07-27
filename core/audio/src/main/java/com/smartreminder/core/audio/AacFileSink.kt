package com.smartreminder.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An [AudioChunkSink] that encodes incoming PCM16 to AAC and writes an `.m4a` container.
 *
 * Uses [MediaCodec] in synchronous mode driven by the recorder thread: each [onChunk] pushes
 * PCM into the encoder's input buffers and drains any produced AAC into the [MediaMuxer].
 * [onStop] signals end-of-stream, drains the tail, and finalizes the file.
 */
class AacFileSink(private val outputFile: File) : AudioChunkSink {

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var presentationTimeUs = 0L
    private var totalSamples = 0L
    private val bufferInfo = MediaCodec.BufferInfo()
    private var failed = false

    init {
        runCatching { start() }.onFailure {
            Log.e(TAG, "Encoder init failed", it)
            failed = true
        }
    }

    private fun start() {
        outputFile.parentFile?.mkdirs()

        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AudioFormat.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_COUNT,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AudioFormat.AAC_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    override fun onChunk(samples: ShortArray, length: Int) {
        if (failed) return
        val codec = codec ?: return

        // PCM16 little-endian bytes for the encoder input.
        val bytes = ByteBuffer.allocate(length * AudioFormat.BYTES_PER_SAMPLE)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until length) bytes.putShort(samples[i])
        bytes.flip()

        var offset = 0
        val data = bytes.array()
        val limit = bytes.limit()
        while (offset < limit) {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex < 0) {
                drain(endOfStream = false)
                continue
            }
            val inBuf = codec.getInputBuffer(inIndex) ?: continue
            inBuf.clear()
            val toCopy = minOf(inBuf.capacity(), limit - offset)
            inBuf.put(data, offset, toCopy)
            offset += toCopy

            val samplesInChunk = toCopy / AudioFormat.BYTES_PER_SAMPLE
            codec.queueInputBuffer(inIndex, 0, toCopy, presentationTimeUs, 0)
            totalSamples += samplesInChunk
            presentationTimeUs = totalSamples * 1_000_000L / AudioFormat.SAMPLE_RATE_HZ
        }
        drain(endOfStream = false)
    }

    override fun onStop() {
        if (!failed) {
            runCatching {
                val codec = codec
                if (codec != null) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US * 5)
                    if (inIndex >= 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, presentationTimeUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                    }
                    drain(endOfStream = true)
                }
            }.onFailure { Log.e(TAG, "Encoder finalize failed", it) }
        }
        release()
    }

    private fun drain(endOfStream: Boolean) {
        val codec = codec ?: return
        val muxer = muxer ?: return
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return else continue
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "Format changed twice" }
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                outIndex >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIndex)
                    if (encoded != null && bufferInfo.size > 0 && muxerStarted &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { if (muxerStarted) muxer?.stop() }
        runCatching { muxer?.release() }
        codec = null
        muxer = null
    }

    private companion object {
        const val TAG = "AacFileSink"
        const val TIMEOUT_US = 10_000L
        const val MAX_INPUT_SIZE = 16 * 1024
    }
}
