package com.smartreminder.core.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt
import android.media.AudioFormat as AndroidAudioFormat

sealed interface RecorderState {
    data object Idle : RecorderState
    data object Recording : RecorderState
    data class Finished(val file: File, val durationMs: Long) : RecorderState
    data class Failed(val reason: String) : RecorderState
}

/**
 * Owns a single [AudioRecord] stream and fans every captured PCM buffer out to the registered
 * [AudioChunkSink]s — the AAC encoder always, plus an optional STT sink (Phase 4). One
 * microphone, two consumers, no re-recording.
 *
 * Reading happens on a dedicated thread; [state] and [amplitude] are observed from the UI.
 */
class VoiceRecorder {

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    /** RMS amplitude of the latest buffer, normalized to 0f..1f, for a live waveform. */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @Volatile private var recording = false
    private var thread: Thread? = null

    val isRecording: Boolean get() = recording

    /**
     * Starts capturing to [outputFile] (an `.m4a`). [extraSink] receives the same PCM buffers,
     * used to plug in live transcription. Returns immediately; observe [state] for completion.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(outputFile: File, extraSink: AudioChunkSink? = null) {
        if (recording) return
        recording = true
        _state.value = RecorderState.Recording
        thread = Thread { runLoop(outputFile, extraSink) }.apply {
            name = "VoiceRecorder"
            start()
        }
    }

    fun stop() {
        recording = false
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun runLoop(outputFile: File, extraSink: AudioChunkSink?) {
        val minBuffer = AudioRecord.getMinBufferSize(
            AudioFormat.SAMPLE_RATE_HZ,
            AndroidAudioFormat.CHANNEL_IN_MONO,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            fail("AudioRecord unsupported (min buffer $minBuffer)")
            return
        }
        val bufferBytes = maxOf(minBuffer, MIN_READ_SAMPLES * AudioFormat.BYTES_PER_SAMPLE)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AudioFormat.SAMPLE_RATE_HZ,
            AndroidAudioFormat.CHANNEL_IN_MONO,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            fail("AudioRecord failed to initialize")
            return
        }

        val encoder = AacFileSink(outputFile)
        val sinks = listOfNotNull(encoder, extraSink)
        val samples = ShortArray(bufferBytes / AudioFormat.BYTES_PER_SAMPLE)
        var totalSamples = 0L

        try {
            record.startRecording()
            while (recording) {
                val read = record.read(samples, 0, samples.size)
                if (read <= 0) continue
                totalSamples += read
                _amplitude.value = rms(samples, read)
                sinks.forEach { it.onChunk(samples, read) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Recording loop error", t)
            fail(t.message ?: "Recording error")
            stopQuietly(record)
            record.release()
            sinks.forEach { it.onStop() }
            return
        }

        stopQuietly(record)
        record.release()
        sinks.forEach { it.onStop() }
        _amplitude.value = 0f

        val durationMs = totalSamples * 1000L / AudioFormat.SAMPLE_RATE_HZ
        _state.value = if (outputFile.exists() && outputFile.length() > 0) {
            RecorderState.Finished(outputFile, durationMs)
        } else {
            RecorderState.Failed("No audio captured")
        }
    }

    private fun fail(reason: String) {
        recording = false
        _amplitude.value = 0f
        _state.value = RecorderState.Failed(reason)
    }

    private fun rms(samples: ShortArray, length: Int): Float {
        if (length == 0) return 0f
        var sum = 0.0
        for (i in 0 until length) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / length)
        // Map to 0..1 with a soft ceiling so speech fills most of the range.
        return min(1f, (rms / MAX_RMS).toFloat())
    }

    private fun stopQuietly(record: AudioRecord) {
        try {
            record.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "Ignored error during teardown", t)
        }
    }

    private companion object {
        const val TAG = "VoiceRecorder"
        const val MIN_READ_SAMPLES = 1600 // 100 ms at 16 kHz
        const val MAX_RMS = 12_000.0
    }
}
