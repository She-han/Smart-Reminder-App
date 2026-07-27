package com.smartreminder.feature.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.smartreminder.core.model.Reminder

object CallNotifications {
    const val CHANNEL_CALL = "reminder_call"
    const val CHANNEL_MISSED = "reminder_missed"

    /** Registered from Application.onCreate. Silent channel — the service owns the ringtone. */
    fun registerChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val callChannel = NotificationChannel(
            CHANNEL_CALL,
            "Reminder calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Rings when a reminder is due"
            setSound(null, null) // ringtone is played by the service so we can loop and route it
            enableVibration(false)
            setBypassDnd(true)
        }

        val missedChannel = NotificationChannel(
            CHANNEL_MISSED,
            "Missed reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Reminders you didn't answer" }

        manager.createNotificationChannel(callChannel)
        manager.createNotificationChannel(missedChannel)
    }

    /**
     * The CallStyle incoming-call notification with a full-screen intent — the same mechanism
     * messaging apps use. On a locked/off screen the full-screen intent launches [CallActivity]
     * directly; otherwise this heads-up notification shows Answer/Decline.
     */
    fun buildIncomingCall(context: Context, reminder: Reminder): Notification {
        val caller = Person.Builder().setName(reminder.title).setImportant(true).build()

        val fullScreen = callActivityIntent(context, reminder.id)
        val fullScreenPi = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val answerPi = servicePendingIntent(context, reminder.id, IncomingCallService.ACTION_ANSWER)
        val declinePi = servicePendingIntent(context, reminder.id, IncomingCallService.ACTION_DECLINE)

        return NotificationCompat.Builder(context, CHANNEL_CALL)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(reminder.title)
            .setContentText(subtitle(reminder))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePi, answerPi))
            .build()
    }

    /** Persistent notification while the call is answered/active, keeping the FGS alive. */
    fun buildOngoingCall(context: Context, reminder: Reminder): Notification {
        val caller = Person.Builder().setName(reminder.title).setImportant(true).build()
        val hangUpPi = servicePendingIntent(context, reminder.id, IncomingCallService.ACTION_END)
        val contentPi = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            callActivityIntent(context, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_CALL)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(reminder.title)
            .setContentText("Playing reminder…")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(caller, hangUpPi))
            .build()
    }

    fun buildMissed(context: Context, reminder: Reminder): Notification {
        val contentPi = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Missed: ${reminder.title}")
            .setContentText(subtitle(reminder))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()
    }

    private fun subtitle(reminder: Reminder): String =
        "${reminder.title} · reminder"

    private fun callActivityIntent(context: Context, reminderId: Long): Intent =
        Intent(context, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(CallActivity.EXTRA_REMINDER_ID, reminderId)
        }

    private fun servicePendingIntent(context: Context, reminderId: Long, action: String): PendingIntent {
        val intent = Intent(context, IncomingCallService::class.java).apply {
            this.action = action
            putExtra(IncomingCallService.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getService(
            context,
            (reminderId.toInt() * 10) + action.hashCode() % 7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
