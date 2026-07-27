package com.smartreminder.core.data

import com.smartreminder.core.data.db.ReminderDao
import com.smartreminder.core.data.db.toDomain
import com.smartreminder.core.data.db.toEntity
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<Reminder>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<List<Reminder>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observe(id: Long): Flow<Reminder?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun get(id: Long): Reminder? = dao.getById(id)?.toDomain()

    suspend fun getActive(): List<Reminder> = dao.getActive().map { it.toDomain() }

    /** Returns the newly assigned id. */
    suspend fun create(reminder: Reminder): Long =
        dao.insert(reminder.copy(createdAt = Instant.now(clock)).toEntity())

    suspend fun update(reminder: Reminder) = dao.update(reminder.toEntity())

    /** Deletes the row **and** its voice-note file. Callers must not delete via the DAO directly. */
    suspend fun delete(reminder: Reminder) {
        reminder.audioPath?.let { path -> File(path).takeIf(File::exists)?.delete() }
        dao.deleteById(reminder.id)
    }

    suspend fun updateStatus(id: Long, status: ReminderStatus) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = status))
    }
}
