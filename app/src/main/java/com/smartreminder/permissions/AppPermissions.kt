package com.smartreminder.permissions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** One item in the permissions checklist, plus how to check and request it. */
enum class AppPermission(val title: String, val rationale: String, val critical: Boolean) {
    MICROPHONE(
        title = "Microphone",
        rationale = "To record your voice notes.",
        critical = true,
    ),
    NOTIFICATIONS(
        title = "Notifications",
        rationale = "So reminders can ring and show on screen.",
        critical = true,
    ),
    EXACT_ALARM(
        title = "Alarms & reminders",
        rationale = "So reminders ring at the exact time, even in Doze.",
        critical = true,
    ),
    FULL_SCREEN(
        title = "Show on lock screen",
        rationale = "So a reminder rings like a call over the lock screen.",
        critical = true,
    ),
    BATTERY(
        title = "Unrestricted battery",
        rationale = "Stops the system from killing reminders in the background.",
        critical = false,
    ),
    ;

    fun isGranted(context: Context): Boolean = when (this) {
        MICROPHONE -> ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        EXACT_ALARM -> context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        FULL_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }

        BATTERY -> context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Settings intent for permissions that can't be requested with a runtime dialog. */
    fun settingsIntent(context: Context): Intent? = when (this) {
        EXACT_ALARM -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:${context.packageName}"))

        FULL_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:${context.packageName}"))
        } else {
            null
        }

        @Suppress("BatteryLife")
        BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

        else -> null
    }

    /** The runtime permission string for dialog-based requests, or null if settings-based. */
    fun runtimePermission(): String? = when (this) {
        MICROPHONE -> android.Manifest.permission.RECORD_AUDIO
        NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

        else -> null
    }

    companion object {
        fun allCriticalGranted(context: Context): Boolean =
            entries.filter { it.critical }.all { it.isGranted(context) }
    }
}
