package com.smartreminder.feature.capture

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.core.alarm.AlarmScheduler
import com.smartreminder.core.audio.PlaybackRoute
import com.smartreminder.core.audio.RecorderState
import com.smartreminder.core.audio.VoiceNotePlayer
import com.smartreminder.core.audio.VoiceNoteStorage
import com.smartreminder.core.audio.VoiceRecorder
import com.smartreminder.core.data.ReminderRepository
import com.smartreminder.core.data.SettingsRepository
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.nlp.ReminderParser
import com.smartreminder.core.stt.ModelState
import com.smartreminder.core.stt.VoskModelManager
import com.smartreminder.core.stt.VoskTranscriber
import com.smartreminder.core.stt.VoskTranscriberFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val modelManager: VoskModelManager,
    private val transcriberFactory: VoskTranscriberFactory,
    private val recorder: VoiceRecorder,
    private val player: VoiceNotePlayer,
    private val storage: VoiceNoteStorage,
    private val parser: ReminderParser,
    private val reminderRepository: ReminderRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _uiState = MutableStateFlow<CaptureUiState>(
        if (modelManager.isReady()) CaptureUiState.Ready() else CaptureUiState.Setup(ModelState.Missing),
    )
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private var transcriber: VoskTranscriber? = null
    private var audioFile: File? = null
    private var recordingStartedAt = 0L
    private var defaultLeadMinutes = Reminder.DEFAULT_LEAD_MINUTES

    init {
        viewModelScope.launch {
            defaultLeadMinutes = settingsRepository.settings.first().defaultLeadMinutes
        }
        // Mirror model state into Setup until it is ready.
        viewModelScope.launch {
            modelManager.state.collect { modelState ->
                if (_uiState.value is CaptureUiState.Setup) {
                    _uiState.value = if (modelState is ModelState.Ready) {
                        CaptureUiState.Ready()
                    } else {
                        CaptureUiState.Setup(modelState)
                    }
                }
            }
        }
    }

    fun downloadModel() {
        viewModelScope.launch { modelManager.ensureModel() }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        if (_uiState.value !is CaptureUiState.Ready) return
        viewModelScope.launch {
            val note = storage.newFile()
            audioFile = note
            transcriber = transcriberFactory.create()
            recordingStartedAt = System.currentTimeMillis()
            recorder.start(note, transcriber)
            trackRecording()
        }
    }

    fun stopRecording() {
        if (_uiState.value !is CaptureUiState.Recording) return
        _uiState.value = CaptureUiState.Processing
        recorder.stop()
        viewModelScope.launch { finishRecording() }
    }

    /** Abort without saving: stop, delete the partial file, return to Ready. */
    fun cancel() {
        recorder.stop()
        viewModelScope.launch {
            recorder.state.first { it is RecorderState.Finished || it is RecorderState.Failed }
            transcriber?.close()
            transcriber = null
            audioFile?.delete()
            audioFile = null
            _uiState.value = CaptureUiState.Ready()
        }
    }

    fun save(draft: ReminderDraft) {
        val date = draft.date
        val time = draft.time
        if (date == null || time == null) return
        viewModelScope.launch {
            val eventAt: Instant = date.atTime(time).atZone(zone).toInstant()
            val reminder = Reminder(
                title = draft.title.trim().ifEmpty { "Reminder" },
                transcript = draft.transcript,
                audioPath = draft.audioPath,
                eventAt = eventAt,
                leadMinutes = draft.leadMinutes,
                createdAt = Instant.now(clock),
            )
            val id = reminderRepository.create(reminder)
            alarmScheduler.schedule(reminder.copy(id = id))
            _uiState.value = CaptureUiState.Saved
        }
    }

    private suspend fun trackRecording() {
        while (_uiState.value is CaptureUiState.Processing ||
            recorder.isRecording ||
            _uiState.value is CaptureUiState.Ready
        ) {
            if (!recorder.isRecording) break
            _uiState.value = CaptureUiState.Recording(
                partialText = transcriber?.transcript?.value?.fullText.orEmpty(),
                amplitude = recorder.amplitude.value,
                elapsedMs = System.currentTimeMillis() - recordingStartedAt,
            )
            delay(80)
        }
    }

    private suspend fun finishRecording() {
        val terminal = recorder.state.first {
            it is RecorderState.Finished || it is RecorderState.Failed
        }
        val stt = transcriber
        val text = stt?.currentText().orEmpty()
        stt?.close()
        transcriber = null

        if (terminal is RecorderState.Failed) {
            audioFile?.delete()
            audioFile = null
            _uiState.value = CaptureUiState.Ready(error = terminal.reason)
            return
        }

        val parsed = parser.parse(text)
        val local = parsed.eventAt
        _uiState.value = CaptureUiState.Confirm(
            ReminderDraft(
                title = parsed.title,
                transcript = text,
                audioPath = audioFile?.absolutePath,
                date = local?.toLocalDate(),
                time = local?.toLocalTime(),
                leadMinutes = defaultLeadMinutes,
                confidence = parsed.confidence,
                matchedSpan = parsed.matchedSpan,
            ),
        )
    }

    fun playPreview(path: String) {
        player.play(File(path), PlaybackRoute.SPEAKER)
    }

    fun stopPreview() {
        player.stop()
    }

    override fun onCleared() {
        recorder.stop()
        transcriber?.close()
        player.release()
    }

    private companion object {
        const val TAG = "CaptureViewModel"
    }
}
