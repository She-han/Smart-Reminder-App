package com.smartreminder.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartreminder.core.data.ReminderRepository
import com.smartreminder.core.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the "call" for a reminder using [AlarmManager.setAlarmClock], the highest-priority
 * exact alarm. It fires even in Doze, shows the system alarm-clock status icon, and — unlike
 * `setExactAndAllowWhileIdle` — is exempt from the Android 12+ exact-alarm permission gate.
 *
 * The [PendingIntent] carries only the reminder id; the receiver reloads the row so it always
 * acts on current data, even if the reminder was edited after scheduling.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ReminderRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: Reminder) {
        if (!reminder.status.isActive) return
        val triggerAt = reminder.alertAt.toEpochMilli()
        val operation = alarmPendingIntent(reminder.id)
        val show = showPendingIntent()

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, show),
            operation,
        )
        Log.d(TAG, "Scheduled reminder ${reminder.id} at $triggerAt")
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(alarmPendingIntent(reminderId))
    }

    /** Cancels then reschedules — used after an edit changes the ring time. */
    fun reschedule(reminder: Reminder) {
        cancel(reminder.id)
        schedule(reminder)
    }

    /** Rebuilds every pending alarm from the database. Called on boot and app replace. */
    suspend fun rescheduleAll() {
        val active = repository.getActive()
        active.forEach { schedule(it) }
        Log.d(TAG, "Rescheduled ${active.size} reminders")
    }

    private fun alarmPendingIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_RING
            // Distinct data per id so PendingIntents don't collapse into one.
            data = android.net.Uri.parse("smartreminder://reminder/$reminderId")
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Tapping the status-bar alarm icon opens the app. */
    private fun showPendingIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            0,
            launch ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_RING = "com.smartreminder.action.RING"
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val TAG = "AlarmScheduler"
    }
}
