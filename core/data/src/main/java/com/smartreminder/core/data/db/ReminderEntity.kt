package com.smartreminder.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderStatus
import java.time.Instant

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val transcript: String,
    val audioPath: String?,
    @ColumnInfo(name = "event_at") val eventAtMillis: Long,
    @ColumnInfo(name = "lead_minutes") val leadMinutes: Int,
    val status: ReminderStatus,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAtMillis: Long?,
    @ColumnInfo(name = "snoozed_until") val snoozedUntilMillis: Long?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    /**
     * Denormalized [Reminder.alertAt] so the DB can answer "what is due next" with an
     * indexed query instead of loading every row and computing in Kotlin.
     * Always written via [toEntity]; never set by hand.
     */
    @ColumnInfo(name = "alert_at", index = true) val alertAtMillis: Long,
)

fun ReminderEntity.toDomain() = Reminder(
    id = id,
    title = title,
    transcript = transcript,
    audioPath = audioPath,
    eventAt = Instant.ofEpochMilli(eventAtMillis),
    leadMinutes = leadMinutes,
    status = status,
    attemptCount = attemptCount,
    lastAttemptAt = lastAttemptAtMillis?.let(Instant::ofEpochMilli),
    snoozedUntil = snoozedUntilMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtMillis),
)

fun Reminder.toEntity() = ReminderEntity(
    id = id,
    title = title,
    transcript = transcript,
    audioPath = audioPath,
    eventAtMillis = eventAt.toEpochMilli(),
    leadMinutes = leadMinutes,
    status = status,
    attemptCount = attemptCount,
    lastAttemptAtMillis = lastAttemptAt?.toEpochMilli(),
    snoozedUntilMillis = snoozedUntil?.toEpochMilli(),
    createdAtMillis = createdAt.toEpochMilli(),
    alertAtMillis = alertAt.toEpochMilli(),
)
