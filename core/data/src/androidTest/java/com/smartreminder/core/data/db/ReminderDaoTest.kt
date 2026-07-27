package com.smartreminder.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class ReminderDaoTest {

    private lateinit var db: ReminderDatabase
    private lateinit var dao: ReminderDao

    private val eventAt: Instant = Instant.parse("2026-07-24T15:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReminderDatabase::class.java,
        ).build()
        dao = db.reminderDao()
    }

    @After
    fun tearDown() = db.close()

    private fun reminder(
        title: String = "Meeting",
        status: ReminderStatus = ReminderStatus.SCHEDULED,
        leadMinutes: Int = 30,
    ) = Reminder(
        title = title,
        transcript = "i have a meeting tomorrow 3pm",
        eventAt = eventAt,
        leadMinutes = leadMinutes,
        status = status,
        createdAt = Instant.parse("2026-07-23T10:00:00Z"),
    )

    @Test
    fun insert_thenGetById_roundTripsAllFields() = runTest {
        val id = dao.insert(reminder().toEntity())

        val loaded = dao.getById(id)!!.toDomain()

        assertEquals("Meeting", loaded.title)
        assertEquals(eventAt, loaded.eventAt)
        assertEquals(30, loaded.leadMinutes)
        assertEquals(ReminderStatus.SCHEDULED, loaded.status)
        assertNull(loaded.audioPath)
    }

    @Test
    fun alertAt_isPersistedAsEventMinusLeadTime() = runTest {
        val id = dao.insert(reminder(leadMinutes = 45).toEntity())

        val entity = dao.getById(id)!!

        assertEquals(
            eventAt.minus(45, ChronoUnit.MINUTES).toEpochMilli(),
            entity.alertAtMillis,
        )
    }

    @Test
    fun snooze_overridesAlertAt() = runTest {
        val snoozeTo = Instant.parse("2026-07-24T14:50:00Z")
        val id = dao.insert(
            reminder().copy(status = ReminderStatus.SNOOZED, snoozedUntil = snoozeTo).toEntity(),
        )

        assertEquals(snoozeTo.toEpochMilli(), dao.getById(id)!!.alertAtMillis)
    }

    @Test
    fun observeAll_isOrderedByAlertTime() = runTest {
        dao.insert(reminder(title = "Later").copy(eventAt = eventAt.plus(2, ChronoUnit.HOURS)).toEntity())
        dao.insert(reminder(title = "Sooner").toEntity())

        val titles = dao.observeAll().first().map { it.title }

        assertEquals(listOf("Sooner", "Later"), titles)
    }

    @Test
    fun getActive_excludesTerminalStatuses() = runTest {
        dao.insert(reminder(title = "Scheduled", status = ReminderStatus.SCHEDULED).toEntity())
        dao.insert(reminder(title = "Snoozed", status = ReminderStatus.SNOOZED).toEntity())
        dao.insert(reminder(title = "Done", status = ReminderStatus.DONE).toEntity())
        dao.insert(reminder(title = "Cancelled", status = ReminderStatus.CANCELLED).toEntity())

        val active = dao.getActive().map { it.title }

        assertEquals(2, active.size)
        assertTrue(active.containsAll(listOf("Scheduled", "Snoozed")))
    }

    @Test
    fun update_persistsStatusAndAttemptCount() = runTest {
        val id = dao.insert(reminder().toEntity())
        val loaded = dao.getById(id)!!.toDomain()

        dao.update(loaded.copy(status = ReminderStatus.MISSED, attemptCount = 2).toEntity())

        val updated = dao.getById(id)!!.toDomain()
        assertEquals(ReminderStatus.MISSED, updated.status)
        assertEquals(2, updated.attemptCount)
    }

    @Test
    fun deleteById_removesRow() = runTest {
        val id = dao.insert(reminder().toEntity())

        dao.deleteById(id)

        assertNull(dao.getById(id))
    }
}
