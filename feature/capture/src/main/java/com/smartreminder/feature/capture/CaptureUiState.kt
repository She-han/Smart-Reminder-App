package com.smartreminder.feature.capture

import com.smartreminder.core.model.ParsedIntent
import com.smartreminder.core.stt.ModelState
import java.time.LocalDate
import java.time.LocalTime

/** Editable draft shown on the confirm screen before a reminder is saved. */
data class ReminderDraft(
    val title: String,
    val transcript: String,
    val audioPath: String?,
    val date: LocalDate?,
    val time: LocalTime?,
    val leadMinutes: Int,
    val confidence: ParsedIntent.Confidence,
    val matchedSpan: IntRange?,
) {
    val hasDateTime: Boolean get() = date != null && time != null
}

/** Top-level phase of the capture flow. */
sealed interface CaptureUiState {
    /** Model still downloading/unpacking; recording is blocked until it's ready. */
    data class Setup(val modelState: ModelState) : CaptureUiState

    /** Ready to record; [error] holds a transient message from a prior failed attempt. */
    data class Ready(val error: String? = null) : CaptureUiState

    data class Recording(
        val partialText: String,
        val amplitude: Float,
        val elapsedMs: Long,
    ) : CaptureUiState

    /** Transcribing/parsing after the user stopped recording. */
    data object Processing : CaptureUiState

    data class Confirm(val draft: ReminderDraft) : CaptureUiState

    /** Reminder persisted; the UI navigates back to the list. */
    data object Saved : CaptureUiState
}
