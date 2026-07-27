package com.smartreminder.core.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartreminder.core.data.ReminderRepository
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var repository: ReminderRepository
    private lateinit var scheduler: AlarmScheduler

    private val eventAt: Instant = Instant.now().plus(2, ChronoUnit.HOURS)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        repository = mockk(relaxed = true)
        scheduler = AlarmScheduler(context, repository)
    }

    private fun reminder(id: Long = 1L, leadMinutes: Int = 30, status: ReminderStatus = ReminderStatus.SCHEDULED) =
        Reminder(id = id, title = "Meeting", eventAt = eventAt, leadMinutes = leadMinutes, status = status)

    @Test
    fun schedule_registersAlarmAtAlertTime() {
        val r = reminder(leadMinutes = 30)
        scheduler.schedule(r)

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull("expected an alarm to be scheduled", scheduled)
        assertEquals(
            eventAt.minus(30, ChronoUnit.MINUTES).toEpochMilli(),
            scheduled!!.triggerAtTime,
        )
    }

    @Test
    fun schedule_ignoresTerminalReminder() {
        scheduler.schedule(reminder(status = ReminderStatus.DONE))
        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun cancel_removesTheAlarm() {
        val r = reminder()
        scheduler.schedule(r)
        assertNotNull(shadowOf(alarmManager).nextScheduledAlarm)

        scheduler.cancel(r.id)
        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun reschedule_usesTheNewAlertTime() {
        val r = reminder(leadMinutes = 30)
        scheduler.schedule(r)

        scheduler.reschedule(r.copy(leadMinutes = 60))

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm
        assertEquals(
            eventAt.minus(60, ChronoUnit.MINUTES).toEpochMilli(),
            scheduled!!.triggerAtTime,
        )
    }

    @Test
    fun rescheduleAll_schedulesEveryActiveReminder() = runTest {
        coEvery { repository.getActive() } returns listOf(
            reminder(id = 1L),
            reminder(id = 2L, leadMinutes = 15),
        )

        scheduler.rescheduleAll()

        assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
