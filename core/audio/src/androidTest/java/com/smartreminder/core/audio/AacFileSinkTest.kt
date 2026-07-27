package com.smartreminder.core.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Feeds a synthetic tone straight into the encoder (no microphone), so this is deterministic
 * on the emulator and validates the PCM -> AAC -> .m4a path in isolation from AudioRecord.
 */
@RunWith(AndroidJUnit4::class)
class AacFileSinkTest {

    private lateinit var outFile: File

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        outFile = File(ctx.cacheDir, "encoder-test-${System.nanoTime()}.m4a")
    }

    @After
    fun tearDown() {
        outFile.delete()
    }

    @Test
    fun encodesOneSecondTone_producesValidDecodableM4a() {
        val sink = AacFileSink(outFile)

        // 1 second of 440 Hz sine at 16 kHz, pushed in 100 ms chunks like the recorder would.
        val chunkSamples = AudioFormat.SAMPLE_RATE_HZ / 10
        var t = 0
        repeat(10) {
            val chunk = ShortArray(chunkSamples) { i ->
                val angle = 2.0 * PI * 440.0 * (t + i) / AudioFormat.SAMPLE_RATE_HZ
                (sin(angle) * 8000).toInt().toShort()
            }
            t += chunkSamples
            sink.onChunk(chunk, chunk.size)
        }
        sink.onStop()

        assertTrue("output file should exist", outFile.exists())
        assertTrue("output file should be non-empty", outFile.length() > 0)

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(outFile.absolutePath)
            assertEquals("should contain exactly one track", 1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME)
            assertTrue("track should be AAC audio, was $mime", mime?.startsWith("audio/") == true)
            assertEquals(
                AudioFormat.SAMPLE_RATE_HZ,
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            )

            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            // ~1s; AAC encoder priming/flush makes this approximate.
            assertTrue("duration ~1s, was ${durationUs}us", durationUs in 800_000..1_300_000)
        } finally {
            extractor.release()
        }
    }
}
