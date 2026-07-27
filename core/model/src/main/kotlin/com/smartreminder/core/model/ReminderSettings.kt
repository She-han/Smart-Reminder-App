package com.smartreminder.core.model

/** User-tunable defaults, persisted in DataStore. */
data class ReminderSettings(
    val defaultLeadMinutes: Int = Reminder.DEFAULT_LEAD_MINUTES,
    val redialIntervalMinutes: Int = DEFAULT_REDIAL_INTERVAL_MINUTES,
    val maxCallAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val ringSeconds: Int = DEFAULT_RING_SECONDS,
    val vibrate: Boolean = true,
    val ringtoneUri: String? = null,
) {
    companion object {
        const val DEFAULT_REDIAL_INTERVAL_MINUTES = 5
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_RING_SECONDS = 45

        val LEAD_TIME_PRESETS = listOf(5, 15, 30, 60)
    }
}
