package com.smartreminder.feature.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.core.alarm.AlarmScheduler
import com.smartreminder.core.data.ReminderRepository
import com.smartreminder.core.model.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class ReminderListUiState(
    val upcoming: List<Reminder> = emptyList(),
    val past: List<Reminder> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && upcoming.isEmpty() && past.isEmpty()
}

@HiltViewModel
class ReminderListViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ReminderListUiState> = repository.observeAll()
        .map { reminders ->
            val now = Instant.now(clock)
            // Split on the event time, not the alert time: a reminder whose call already
            // rang but whose meeting hasn't happened yet still belongs under "Upcoming".
            val (upcoming, past) = reminders.partition { it.eventAt >= now }
            ReminderListUiState(
                upcoming = upcoming,
                past = past.sortedByDescending { it.eventAt },
                loading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReminderListUiState(),
        )

    fun delete(reminder: Reminder) {
        viewModelScope.launch {
            alarmScheduler.cancel(reminder.id)
            repository.delete(reminder)
        }
    }
}
