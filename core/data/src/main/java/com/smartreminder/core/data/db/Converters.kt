package com.smartreminder.core.data.db

import androidx.room.TypeConverter
import com.smartreminder.core.model.ReminderStatus

class Converters {
    @TypeConverter
    fun statusToString(status: ReminderStatus): String = status.name

    /** Unknown names (e.g. a status removed in a later version) degrade to CANCELLED rather than crashing. */
    @TypeConverter
    fun stringToStatus(value: String): ReminderStatus =
        runCatching { ReminderStatus.valueOf(value) }.getOrDefault(ReminderStatus.CANCELLED)
}
