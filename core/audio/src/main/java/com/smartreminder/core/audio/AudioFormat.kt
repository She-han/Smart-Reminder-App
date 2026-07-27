package com.smartreminder.core.audio

/**
 * Capture format shared by the recorder, the encoder, and the STT engine.
 *
 * 16 kHz mono PCM16 is what Vosk's acoustic models expect, so recording natively at this
 * rate means no resampling before transcription — the same buffer feeds both the STT sink
 * and the AAC encoder.
 */

object AudioFormat {
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNEL_COUNT = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8

    /** AAC bitrate for the saved .m4a. 32 kbps is plenty for 16 kHz mono speech. */
    const val AAC_BITRATE = 32_000
}

