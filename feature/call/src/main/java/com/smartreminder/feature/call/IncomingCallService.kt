package com.smartreminder.feature.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.smartreminder.core.alarm.RedialCoordinator
import com.smartreminder.core.audio.PlaybackRoute
import com.smartreminder.core.audio.VoiceNotePlayer
import com.smartreminder.core.data.ReminderRepository
import com.smartreminder.core.data.SettingsRepository
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

/**
 * Foreground service that makes a reminder ring like an incoming phone call and, on answer,
 * plays the original voice note through the earpiece.
 *
 * It owns everything with a lifetime longer than the (dismissable) [CallActivity]: the
 * ringtone loop, vibration, the CallStyle notification, audio focus, and the proximity wake
 * lock that blanks the screen at the ear. State is published through [CallStateHolder].
 */
@AndroidEntryPoint
class IncomingCallService : Service() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var player: VoiceNotePlayer
    @Inject lateinit var redial: RedialCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ringtone: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var proximityLock: PowerManager.WakeLock? = null
    private var ringTimeoutJob: Job? = null
    private var elapsedJob: Job? = null
    private var current: Reminder? = null
    private var answeredAt = 0L
    private var onSpeaker = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_INCOMING -> startRinging(reminderId)
            ACTION_ANSWER -> answer()
            ACTION_DECLINE -> decline()
            ACTION_END -> markDoneAndStop()
            ACTION_SNOOZE -> snooze()
            ACTION_SPEAKER -> toggleSpeaker()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(reminderId: Long) {
        scope.launch {
            val reminder = repository.get(reminderId) ?: run { stopSelf(); return@launch }
            current = reminder
            repository.updateStatus(reminderId, ReminderStatus.RINGING)
            CallStateHolder.state.value = CallState.Ringing(reminder)

            val notification = CallNotifications.buildIncomingCall(this@IncomingCallService, reminder)
            startAsForeground(reminder.id.toInt(), notification)

            startRingtone()
            startVibration()

            val ringSeconds = settings.settings.first().ringSeconds
            ringTimeoutJob = scope.launch {
                delay(ringSeconds * 1000L)
                Log.d(TAG, "Ring timed out")
                decline()
            }
        }
    }

    private fun answer() {
        val reminder = current ?: return
        ringTimeoutJob?.cancel()
        stopRingtone()
        stopVibration()

        scope.launch { redial.onAnswered(reminder.id) }

        // Route playback to the earpiece and blank the screen when held to the ear.
        val audioManager = getSystemService(AudioManager::class.java)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
        onSpeaker = false
        acquireProximityLock()

        NotificationManagerCompat.from(this).notify(
            reminder.id.toInt(),
            CallNotifications.buildOngoingCall(this, reminder),
        )

        answeredAt = System.currentTimeMillis()
        val audioPath = reminder.audioPath
        if (audioPath != null && File(audioPath).exists()) {
            player.play(File(audioPath), PlaybackRoute.EARPIECE)
        }
        startElapsedUpdates(reminder)
    }

    private fun toggleSpeaker() {
        val audioManager = getSystemService(AudioManager::class.java)
        onSpeaker = !onSpeaker
        audioManager.isSpeakerphoneOn = onSpeaker
        val reminder = current ?: return
        CallStateHolder.state.value = CallState.InCall(
            reminder = reminder,
            elapsedMs = System.currentTimeMillis() - answeredAt,
            onSpeaker = onSpeaker,
        )
    }

    private fun startElapsedUpdates(reminder: Reminder) {
        elapsedJob?.cancel()
        elapsedJob = scope.launch {
            while (true) {
                CallStateHolder.state.value = CallState.InCall(
                    reminder = reminder,
                    elapsedMs = System.currentTimeMillis() - answeredAt,
                    onSpeaker = onSpeaker,
                )
                delay(500)
            }
        }
    }

    private fun decline() {
        val reminder = current
        scope.launch {
            if (reminder != null) {
                // Redial up to the limit, then leave a persistent "missed" notification.
                when (val outcome = redial.onMissedOrDeclined(reminder.id)) {
                    is RedialCoordinator.MissOutcome.FinalMiss ->
                        NotificationManagerCompat.from(this@IncomingCallService).notify(
                            reminder.id.toInt() + MISSED_ID_OFFSET,
                            CallNotifications.buildMissed(this@IncomingCallService, outcome.reminder),
                        )

                    else -> Unit // redial scheduled; nothing to show now
                }
            }
            cleanupAndStop()
        }
    }

    private fun snooze() {
        val reminder = current
        scope.launch {
            if (reminder != null) redial.onSnooze(reminder.id)
            cleanupAndStop()
        }
    }

    private fun markDoneAndStop() {
        val reminder = current
        scope.launch {
            if (reminder != null) redial.onDone(reminder.id)
            cleanupAndStop()
        }
    }

    private fun cleanupAndStop() {
        CallStateHolder.state.value = CallState.Ended
        stopRingtone()
        stopVibration()
        releaseProximityLock()
        runCatching {
            getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        }
        player.release()
        elapsedJob?.cancel()
        ringTimeoutJob?.cancel()
        current?.let { NotificationManagerCompat.from(this).cancel(it.id.toInt()) }
        CallStateHolder.state.value = CallState.None
        stopForegroundCompat()
        stopSelf()
    }

    private fun startRingtone() {
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = MediaPlayer().apply {
                setDataSource(this@IncomingCallService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.e(TAG, "Ringtone failed", it) }
    }

    private fun stopRingtone() {
        runCatching { ringtone?.stop() }
        runCatching { ringtone?.release() }
        ringtone = null
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        scope.launch {
            if (!settings.settings.first().vibrate) return@launch
            val vib = getSystemService(Vibrator::class.java) ?: return@launch
            vibrator = vib
            val pattern = longArrayOf(0, 800, 800)
            vib.vibrate(VibrationEffect.createWaveform(pattern, 1))
        }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun acquireProximityLock() {
        runCatching {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityLock = pm.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "smartreminder:call",
                ).apply { acquire(10 * 60 * 1000L) }
            }
        }
    }

    private fun releaseProximityLock() {
        runCatching { if (proximityLock?.isHeld == true) proximityLock?.release() }
        proximityLock = null
    }

    private fun startAsForeground(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(id, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val ACTION_INCOMING = "com.smartreminder.call.INCOMING"
        const val ACTION_ANSWER = "com.smartreminder.call.ANSWER"
        const val ACTION_DECLINE = "com.smartreminder.call.DECLINE"
        const val ACTION_END = "com.smartreminder.call.END"
        const val ACTION_SNOOZE = "com.smartreminder.call.SNOOZE"
        const val ACTION_SPEAKER = "com.smartreminder.call.SPEAKER"
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val MISSED_ID_OFFSET = 100_000
        private const val TAG = "IncomingCallService"

        fun start(context: Context, reminderId: Long) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                action = ACTION_INCOMING
                putExtra(EXTRA_REMINDER_ID, reminderId)
            }
            context.startForegroundService(intent)
        }

        fun sendAction(context: Context, action: String, reminderId: Long) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                this.action = action
                putExtra(EXTRA_REMINDER_ID, reminderId)
            }
            context.startService(intent)
        }
    }
}
