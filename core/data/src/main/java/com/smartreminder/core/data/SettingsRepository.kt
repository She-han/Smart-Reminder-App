package com.smartreminder.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<ReminderSettings> = dataStore.data.map { prefs ->
        ReminderSettings(
            defaultLeadMinutes = prefs[Keys.LEAD_MINUTES] ?: Reminder.DEFAULT_LEAD_MINUTES,
            redialIntervalMinutes = prefs[Keys.REDIAL_INTERVAL]
                ?: ReminderSettings.DEFAULT_REDIAL_INTERVAL_MINUTES,
            maxCallAttempts = prefs[Keys.MAX_ATTEMPTS] ?: ReminderSettings.DEFAULT_MAX_ATTEMPTS,
            snoozeMinutes = prefs[Keys.SNOOZE_MINUTES] ?: ReminderSettings.DEFAULT_SNOOZE_MINUTES,
            ringSeconds = prefs[Keys.RING_SECONDS] ?: ReminderSettings.DEFAULT_RING_SECONDS,
            vibrate = prefs[Keys.VIBRATE] ?: true,
            ringtoneUri = prefs[Keys.RINGTONE_URI],
        )
    }

    suspend fun setDefaultLeadMinutes(value: Int) = put(Keys.LEAD_MINUTES, value)
    suspend fun setRedialIntervalMinutes(value: Int) = put(Keys.REDIAL_INTERVAL, value)
    suspend fun setMaxCallAttempts(value: Int) = put(Keys.MAX_ATTEMPTS, value)
    suspend fun setSnoozeMinutes(value: Int) = put(Keys.SNOOZE_MINUTES, value)
    suspend fun setRingSeconds(value: Int) = put(Keys.RING_SECONDS, value)

    suspend fun setVibrate(value: Boolean) {
        dataStore.edit { it[Keys.VIBRATE] = value }
    }

    suspend fun setRingtoneUri(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.RINGTONE_URI) else prefs[Keys.RINGTONE_URI] = value
        }
    }

    private suspend fun put(key: Preferences.Key<Int>, value: Int) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val LEAD_MINUTES = intPreferencesKey("default_lead_minutes")
        val REDIAL_INTERVAL = intPreferencesKey("redial_interval_minutes")
        val MAX_ATTEMPTS = intPreferencesKey("max_call_attempts")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val RING_SECONDS = intPreferencesKey("ring_seconds")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val RINGTONE_URI = stringPreferencesKey("ringtone_uri")
    }
}
