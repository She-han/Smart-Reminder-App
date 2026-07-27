package com.smartreminder.feature.call

import com.smartreminder.core.model.Reminder
import kotlinx.coroutines.flow.MutableStateFlow

/** Current state of the ringing/answered "call", shared between the service and the activity. */
sealed interface CallState {
    data object None : CallState

    data class Ringing(val reminder: Reminder) : CallState

    data class InCall(
        val reminder: Reminder,
        val elapsedMs: Long,
        val onSpeaker: Boolean,
    ) : CallState

    data object Ended : CallState
}

/**
 * Process-wide holder so the foreground service (which owns ringtone/playback) and the
 * full-screen [CallActivity] (which owns the UI) observe the same call without binding.
 */
object CallStateHolder {
    val state = MutableStateFlow<CallState>(CallState.None)
}
