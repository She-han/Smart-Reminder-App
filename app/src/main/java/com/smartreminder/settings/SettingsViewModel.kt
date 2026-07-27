package com.smartreminder.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.core.data.SettingsRepository
import com.smartreminder.core.model.ReminderSettings
import com.smartreminder.core.stt.VoskModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelManager: VoskModelManager,
) : ViewModel() {

    val settings: StateFlow<ReminderSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReminderSettings(),
    )

    fun setDefaultLead(minutes: Int) = update { settingsRepository.setDefaultLeadMinutes(minutes) }
    fun setRedialInterval(minutes: Int) = update { settingsRepository.setRedialIntervalMinutes(minutes) }
    fun setMaxAttempts(count: Int) = update { settingsRepository.setMaxCallAttempts(count) }
    fun setSnooze(minutes: Int) = update { settingsRepository.setSnoozeMinutes(minutes) }
    fun setRingSeconds(seconds: Int) = update { settingsRepository.setRingSeconds(seconds) }
    fun setVibrate(enabled: Boolean) = update { settingsRepository.setVibrate(enabled) }

    fun deleteModel() {
        modelManager.deleteModel()
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
