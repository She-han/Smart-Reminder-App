package com.smartreminder.core.stt

import android.util.Log
import com.smartreminder.core.audio.AudioChunkSink
import com.smartreminder.core.audio.AudioFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.Closeable

/** Live transcription state: settled utterances plus the in-flight [partial]. */
data class TranscriptResult(
    val finalText: String = "",
    val partial: String = "",
) {
    /** Everything recognized so far, for display and for handing to the parser. */
    val fullText: String get() = listOf(finalText, partial).filter { it.isNotBlank() }.joinToString(" ").trim()
}

/**
 * An [AudioChunkSink] that feeds captured PCM into a Vosk [Recognizer] and publishes a running
 * transcript. Plugs into [com.smartreminder.core.audio.VoiceRecorder] alongside the AAC encoder,
 * so recording and transcription share one microphone stream.
 *
 * Not thread-safe: the recorder calls [onChunk]/[onStop] serially from its capture thread.
 */
class VoskTranscriber(
    model: Model,
) : AudioChunkSink, Closeable {

    private val recognizer = Recognizer(model, AudioFormat.SAMPLE_RATE_HZ.toFloat())

    private val _transcript = MutableStateFlow(TranscriptResult())
    val transcript: StateFlow<TranscriptResult> = _transcript.asStateFlow()

    private var finalText = ""

    override fun onChunk(samples: ShortArray, length: Int) {
        try {
            if (recognizer.acceptWaveForm(samples, length)) {
                val text = JSONObject(recognizer.result).optString("text").trim()
                if (text.isNotEmpty()) {
                    finalText = joinNonBlank(finalText, text)
                    _transcript.value = TranscriptResult(finalText, "")
                }
            } else {
                val partial = JSONObject(recognizer.partialResult).optString("partial").trim()
                _transcript.value = TranscriptResult(finalText, partial)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "acceptWaveForm failed", t)
        }
    }

    override fun onStop() {
        try {
            val text = JSONObject(recognizer.finalResult).optString("text").trim()
            if (text.isNotEmpty()) finalText = joinNonBlank(finalText, text)
        } catch (t: Throwable) {
            Log.e(TAG, "finalResult failed", t)
        }
        _transcript.value = TranscriptResult(finalText, "")
    }

    fun currentText(): String = _transcript.value.fullText

    override fun close() {
        recognizer.close()
    }

    private fun joinNonBlank(a: String, b: String): String =
        listOf(a, b).filter { it.isNotBlank() }.joinToString(" ")

    private companion object {
        const val TAG = "VoskTranscriber"
    }
}
