package com.smartreminder.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY alert_at ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status IN ('SCHEDULED', 'SNOOZED', 'RINGING') ORDER BY alert_at ASC")
    fun observeActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observeById(id: Long): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    /** Used by BootReceiver to rebuild every pending alarm after a reboot. */
    @Query("SELECT * FROM reminders WHERE status IN ('SCHEDULED', 'SNOOZED', 'RINGING')")
    suspend fun getActive(): List<ReminderEntity>

    @Insert
    suspend fun insert(entity: ReminderEntity): Long

    @Update
    suspend fun update(entity: ReminderEntity)

    @Delete
    suspend fun delete(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
