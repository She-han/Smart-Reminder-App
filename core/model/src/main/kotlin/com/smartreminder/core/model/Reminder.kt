package com.smartreminder.core.model

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A single reminder.
 *
 * [eventAt] is the time of the thing being remembered (the meeting itself).
 * The app rings at [alertAt], which is [leadMinutes] before that.
 */
data class Reminder(
    val id: Long = 0L,
    val title: String,
    val transcript: String = "",
    val audioPath: String? = null,
    val eventAt: Instant,
    val leadMinutes: Int = DEFAULT_LEAD_MINUTES,
    val status: ReminderStatus = ReminderStatus.SCHEDULED,
    val attemptCount: Int = 0,
    val lastAttemptAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    val createdAt: Instant = Instant.EPOCH,
) {
    /**
     * When the phone should ring. A snooze overrides the normal lead-time calculation,
     * so this is the single source of truth for scheduling — never compute the alarm
     * time from [eventAt] directly.
     */
    val alertAt: Instant
        get() = snoozedUntil ?: eventAt.minus(leadMinutes.toLong(), ChronoUnit.MINUTES)

    val hasAudio: Boolean get() = audioPath != null

    fun isActive(): Boolean = status.isActive

    companion object {
        const val DEFAULT_LEAD_MINUTES = 30
    }
}
