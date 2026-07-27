package com.smartreminder.core.alarm

import com.smartreminder.core.data.ReminderRepository
import com.smartreminder.core.data.SettingsRepository
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderSettings
import com.smartreminder.core.model.ReminderStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class RedialCoordinatorTest {

    private val now: Instant = Instant.parse("2026-07-24T14:30:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var repository: ReminderRepository
    private lateinit var settings: SettingsRepository
    private lateinit var scheduler: AlarmScheduler
    private lateinit var coordinator: RedialCoordinator

    private val config = ReminderSettings(
        redialIntervalMinutes = 5,
        maxCallAttempts = 3,
        snoozeMinutes = 10,
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        settings = mockk()
        scheduler = mockk(relaxed = true)
        every { settings.settings } returns flowOf(config)
        coordinator = RedialCoordinator(repository, settings, scheduler, clock)
    }

    private fun reminder(attemptCount: Int = 0) = Reminder(
        id = 7L,
        title = "Meeting",
        eventAt = Instant.parse("2026-07-24T15:00:00Z"),
        leadMinutes = 30,
        status = ReminderStatus.RINGING,
        attemptCount = attemptCount,
    )

    @Test
    fun missBeforeLimit_reschedulesRedial() = runTest {
        coEvery { repository.get(7L) } returns reminder(attemptCount = 0)
        val saved = slot<Reminder>()

        val outcome = coordinator.onMissedOrDeclined(7L)

        assertTrue(outcome is RedialCoordinator.MissOutcome.Redialing)
        coVerify { repository.update(capture(saved)) }
        assertEquals(ReminderStatus.SCHEDULED, saved.captured.status)
        assertEquals(1, saved.captured.attemptCount)
        assertEquals(now.plus(5, ChronoUnit.MINUTES), saved.captured.snoozedUntil)
        // alertAt now derives from the redial time, not the original lead.
        assertEquals(now.plus(5, ChronoUnit.MINUTES), saved.captured.alertAt)
        coVerify { scheduler.schedule(saved.captured) }
    }

    @Test
    fun missAtLimit_marksMissedAndCancels() = runTest {
        coEvery { repository.get(7L) } returns reminder(attemptCount = 2)
        val saved = slot<Reminder>()

        val outcome = coordinator.onMissedOrDeclined(7L)

        assertTrue(outcome is RedialCoordinator.MissOutcome.FinalMiss)
        coVerify { repository.update(capture(saved)) }
        assertEquals(ReminderStatus.MISSED, saved.captured.status)
        assertEquals(3, saved.captured.attemptCount)
        coVerify { scheduler.cancel(7L) }
    }

    @Test
    fun snooze_reschedulesOnceAtSnoozeTime() = runTest {
        coEvery { repository.get(7L) } returns reminder()
        val saved = slot<Reminder>()

        coordinator.onSnooze(7L)

        coVerify { repository.update(capture(saved)) }
        assertEquals(ReminderStatus.SNOOZED, saved.captured.status)
        assertEquals(now.plus(10, ChronoUnit.MINUTES), saved.captured.alertAt)
        coVerify { scheduler.schedule(saved.captured) }
    }

    @Test
    fun done_cancelsPendingAlarm() = runTest {
        coordinator.onDone(7L)

        coVerify { repository.updateStatus(7L, ReminderStatus.DONE) }
        coVerify { scheduler.cancel(7L) }
    }
}
