package com.smartreminder.core.alarm

import com.smartreminder.core.data.ReminderRepository
import com.smartreminder.core.data.SettingsRepository
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderStatus
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns what happens after a "call" ends: redial on a missed/declined ring up to a limit, snooze
 * once, or finish. Reschedules through [AlarmScheduler] using [Reminder.snoozedUntil] as the
 * next-ring override, so the alarm fires at the redial/snooze time rather than the original lead.
 */
@Singleton
class RedialCoordinator @Inject constructor(
    private val repository: ReminderRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
) {
    /** Outcome of a missed/declined ring, so the caller can show the right notification. */
    sealed interface MissOutcome {
        data class Redialing(val reminder: Reminder, val nextAt: Instant) : MissOutcome
        data class FinalMiss(val reminder: Reminder) : MissOutcome
        data object Unknown : MissOutcome
    }

    /** Declined or rang out. Schedules the next attempt, or gives up after the limit. */
    suspend fun onMissedOrDeclined(reminderId: Long): MissOutcome {
        val reminder = repository.get(reminderId) ?: return MissOutcome.Unknown
        val config = settings.settings.first()
        val now = Instant.now(clock)
        val attempts = reminder.attemptCount + 1

        return if (attempts < config.maxCallAttempts) {
            val nextAt = now.plus(config.redialIntervalMinutes.toLong(), ChronoUnit.MINUTES)
            val updated = reminder.copy(
                status = ReminderStatus.SCHEDULED,
                attemptCount = attempts,
                lastAttemptAt = now,
                snoozedUntil = nextAt,
            )
            repository.update(updated)
            scheduler.schedule(updated)
            MissOutcome.Redialing(updated, nextAt)
        } else {
            val updated = reminder.copy(
                status = ReminderStatus.MISSED,
                attemptCount = attempts,
                lastAttemptAt = now,
            )
            repository.update(updated)
            scheduler.cancel(reminderId)
            MissOutcome.FinalMiss(updated)
        }
    }

    /** User chose snooze: ring once more after the snooze interval, cancelling further redials. */
    suspend fun onSnooze(reminderId: Long) {
        val reminder = repository.get(reminderId) ?: return
        val config = settings.settings.first()
        val nextAt = Instant.now(clock).plus(config.snoozeMinutes.toLong(), ChronoUnit.MINUTES)
        val updated = reminder.copy(
            status = ReminderStatus.SNOOZED,
            snoozedUntil = nextAt,
            lastAttemptAt = Instant.now(clock),
        )
        repository.update(updated)
        scheduler.schedule(updated)
    }

    suspend fun onAnswered(reminderId: Long) {
        repository.updateStatus(reminderId, ReminderStatus.ANSWERED)
    }

    /** Answered and marked done, or otherwise completed. Cancels any pending alarm. */
    suspend fun onDone(reminderId: Long) {
        repository.updateStatus(reminderId, ReminderStatus.DONE)
        scheduler.cancel(reminderId)
    }
}
