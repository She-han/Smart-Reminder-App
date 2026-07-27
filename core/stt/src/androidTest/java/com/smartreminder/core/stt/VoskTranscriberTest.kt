package com.smartreminder.core.stt

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.smartreminder.core.audio.AudioFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.vosk.Model
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end STT proof: synthesize real speech with Android TTS, resample to 16 kHz, and run it
 * through the Vosk pipeline. Asserts the recognizer returns actual words. TTS availability is a
 * device precondition (skipped via assume if the emulator has no engine), but once speech is
 * produced, transcription is asserted for real.
 */
class VoskTranscriberTest {

    @Test
    fun transcribesSynthesizedSpeech(): Unit = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        val manager = VoskModelManager(ctx)
        assertTrue("model must install", manager.ensureModel().isSuccess)
        val dir = manager.readyDirOrNull()!!

        val wav = File(ctx.cacheDir, "speech-${System.nanoTime()}.wav")
        val spoken = synthesize(ctx, "meeting tomorrow at three pm", wav)
        assumeTrue("TTS engine unavailable on this device", spoken)

        val samples = WavPcm.readMono(wav, AudioFormat.SAMPLE_RATE_HZ)
        assumeTrue("TTS produced no audio", samples.size > AudioFormat.SAMPLE_RATE_HZ / 2)

        val model = Model(dir.absolutePath)
        val transcriber = VoskTranscriber(model)
        try {
            var offset = 0
            val chunk = AudioFormat.SAMPLE_RATE_HZ / 5 // 200 ms
            while (offset < samples.size) {
                val len = minOf(chunk, samples.size - offset)
                transcriber.onChunk(samples.copyOfRange(offset, offset + len), len)
                offset += len
            }
            transcriber.onStop()
        } finally {
            transcriber.close()
            model.close()
        }

        val text = transcriber.currentText()
        Log.i("VoskTranscriberTest", "Transcript: '$text'")
        assertTrue("expected non-empty transcript, got '$text'", text.isNotBlank())
        // Vosk on synthesized speech won't be perfect; assert it caught a salient word.
        val hit = listOf("meeting", "tomorrow", "three").any { text.contains(it, ignoreCase = true) }
        assertTrue("transcript '$text' should contain a keyword", hit)
        wav.delete()
    }

    private fun synthesize(ctx: android.content.Context, text: String, out: File): Boolean {
        val initLatch = CountDownLatch(1)
        var initOk = false
        val tts = TextToSpeech(ctx) { status ->
            initOk = status == TextToSpeech.SUCCESS
            initLatch.countDown()
        }
        if (!initLatch.await(10, TimeUnit.SECONDS) || !initOk) {
            runCatching { tts.shutdown() }
            return false
        }
        tts.language = Locale.US

        val doneLatch = CountDownLatch(1)
        var ok = false
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                ok = true
                doneLatch.countDown()
            }

            @Deprecated("required override")
            override fun onError(utteranceId: String?) {
                doneLatch.countDown()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                doneLatch.countDown()
            }
        })

        val params = android.os.Bundle()
        tts.synthesizeToFile(text, params, out, "utt-1")
        doneLatch.await(20, TimeUnit.SECONDS)
        runCatching { tts.shutdown() }
        return ok && out.exists() && out.length() > 0
    }
}
