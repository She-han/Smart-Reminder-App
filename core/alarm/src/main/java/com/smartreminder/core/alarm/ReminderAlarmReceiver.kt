package com.smartreminder.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fires when a reminder's alarm goes off. Delegates to the injected [AlarmRinger]; the receiver
 * itself knows nothing about notifications or the call UI.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var ringer: AlarmRinger

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_RING) return
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return
        Log.d(TAG, "Alarm fired for reminder $reminderId")
        ringer.ring(reminderId)
    }

    private companion object {
        const val TAG = "ReminderAlarmReceiver"
    }
}
