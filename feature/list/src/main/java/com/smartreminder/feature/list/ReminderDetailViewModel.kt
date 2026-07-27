package com.smartreminder.feature.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.core.alarm.AlarmScheduler
import com.smartreminder.core.audio.PlaybackRoute
import com.smartreminder.core.audio.VoiceNotePlayer
import com.smartreminder.core.data.ReminderRepository
import com.smartreminder.core.data.SettingsRepository
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class ReminderDetailUiState(
    val loading: Boolean = true,
    val isNew: Boolean = false,
    val existing: Reminder? = null,
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val leadMinutes: Int = Reminder.DEFAULT_LEAD_MINUTES,
    val transcript: String = "",
    val audioPath: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReminderRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val player: VoiceNotePlayer,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val reminderId: Long = savedStateHandle.get<Long>("id") ?: 0L

    private val _uiState = MutableStateFlow(ReminderDetailUiState())
    val uiState: StateFlow<ReminderDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (reminderId == 0L) {
                val defaultLead = settingsRepository.settings.first().defaultLeadMinutes
                val start = Instant.now(clock).plusSeconds(3600).atZone(zone)
                _uiState.value = ReminderDetailUiState(
                    loading = false,
                    isNew = true,
                    date = start.toLocalDate(),
                    time = start.toLocalTime().withSecond(0).withNano(0),
                    leadMinutes = defaultLead,
                )
            } else {
                val reminder = repository.get(reminderId)
                if (reminder == null) {
                    _uiState.value = _uiState.value.copy(loading = false, deleted = true)
                } else {
                    val local = reminder.eventAt.atZone(zone)
                    _uiState.value = ReminderDetailUiState(
                        loading = false,
                        isNew = false,
                        existing = reminder,
                        title = reminder.title,
                        date = local.toLocalDate(),
                        time = local.toLocalTime(),
                        leadMinutes = reminder.leadMinutes,
                        transcript = reminder.transcript,
                        audioPath = reminder.audioPath,
                    )
                }
            }
        }
    }

    fun onTitleChange(value: String) { _uiState.value = _uiState.value.copy(title = value) }
    fun onDateChange(value: LocalDate) { _uiState.value = _uiState.value.copy(date = value) }
    fun onTimeChange(value: LocalTime) { _uiState.value = _uiState.value.copy(time = value) }
    fun onLeadChange(value: Int) { _uiState.value = _uiState.value.copy(leadMinutes = value) }

    fun save() {
        val s = _uiState.value
        val eventAt = s.date.atTime(s.time).atZone(zone).toInstant()
        viewModelScope.launch {
            if (s.isNew) {
                val reminder = Reminder(
                    title = s.title.trim().ifEmpty { "Reminder" },
                    eventAt = eventAt,
                    leadMinutes = s.leadMinutes,
                    createdAt = Instant.now(clock),
                )
                val id = repository.create(reminder)
                scheduler.schedule(reminder.copy(id = id))
            } else {
                val existing = s.existing ?: return@launch
                // Editing resets the call lifecycle: a fresh SCHEDULED reminder, no leftover
                // snooze/redial state, then cancel + reschedule so no stale alarm survives.
                val updated = existing.copy(
                    title = s.title.trim().ifEmpty { "Reminder" },
                    eventAt = eventAt,
                    leadMinutes = s.leadMinutes,
                    status = ReminderStatus.SCHEDULED,
                    attemptCount = 0,
                    snoozedUntil = null,
                )
                repository.update(updated)
                scheduler.reschedule(updated)
            }
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }

    fun delete() {
        val existing = _uiState.value.existing
        viewModelScope.launch {
            if (existing != null) {
                scheduler.cancel(existing.id)
                repository.delete(existing)
            }
            _uiState.value = _uiState.value.copy(deleted = true)
        }
    }

    fun play() {
        _uiState.value.audioPath?.let { path ->
            val file = File(path)
            if (file.exists()) player.play(file, PlaybackRoute.SPEAKER)
        }
    }

    fun stopPlay() = player.stop()

    override fun onCleared() {
        player.release()
    }
}
