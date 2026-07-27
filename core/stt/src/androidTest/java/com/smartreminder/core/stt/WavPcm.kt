package com.smartreminder.core.stt

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal RIFF/WAVE reader + resampler, used only to feed TTS-synthesized speech into Vosk. */
object WavPcm {

    /** Reads a PCM16 WAV and returns mono samples resampled to [targetRate] Hz. */
    fun readMono(file: File, targetRate: Int): ShortArray {
        val bytes = file.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        require(bytes.size > 44) { "WAV too small" }
        require(bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray())) { "not RIFF" }
        require(bytes.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) { "not WAVE" }

        var channels = 1
        var sampleRate = targetRate
        var bitsPerSample = 16
        var dataOffset = -1
        var dataLen = 0

        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    channels = bb.getShort(body + 2).toInt()
                    sampleRate = bb.getInt(body + 4)
                    bitsPerSample = bb.getShort(body + 14).toInt()
                }
                "data" -> {
                    dataOffset = body
                    dataLen = size
                }
            }
            pos = body + size + (size and 1) // chunks are word-aligned
        }
        require(dataOffset >= 0 && bitsPerSample == 16) { "unsupported WAV: bits=$bitsPerSample" }

        val safeLen = minOf(dataLen, bytes.size - dataOffset)
        val frameCount = safeLen / (2 * channels)
        val mono = ShortArray(frameCount)
        val src = ByteBuffer.wrap(bytes, dataOffset, safeLen).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            var sum = 0
            for (c in 0 until channels) sum += src.short.toInt()
            mono[i] = (sum / channels).toShort()
        }

        return if (sampleRate == targetRate) mono else resampleLinear(mono, sampleRate, targetRate)
    }

    private fun resampleLinear(input: ShortArray, from: Int, to: Int): ShortArray {
        if (input.isEmpty()) return input
        val outLen = (input.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        val ratio = (input.size - 1).toDouble() / (outLen - 1).coerceAtLeast(1)
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            val a = input[idx].toDouble()
            val b = input[minOf(idx + 1, input.size - 1)].toDouble()
            out[i] = (a + (b - a) * frac).toInt().toShort()
        }
        return out
    }
}
