package com.smartreminder.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reschedules every pending reminder after a reboot or app update. Alarms do not survive either
 * event, so without this the app would silently stop ringing until each reminder was re-saved.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Rescheduling after ${intent.action}")
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        scheduler.rescheduleAll()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
