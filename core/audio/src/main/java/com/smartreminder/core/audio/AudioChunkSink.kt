package com.smartreminder.core.audio

/**
 * Consumes raw PCM16 chunks as they are captured. The recorder fans each buffer out to every
 * registered sink, so the AAC encoder and the STT engine both see the same audio without a
 * second microphone stream.
 */
interface AudioChunkSink {
    /** [samples] holds [length] valid PCM16 samples; the rest of the array is stale. */
    fun onChunk(samples: ShortArray, length: Int)

    /** Recording has stopped; flush and release. Always called once, even on error. */
    fun onStop()
}
