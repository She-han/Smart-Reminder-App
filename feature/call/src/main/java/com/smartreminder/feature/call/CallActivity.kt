package com.smartreminder.feature.call

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.smartreminder.core.ui.theme.SmartReminderTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen "incoming call" activity, launched by the service's full-screen intent. Shows
 * over the lock screen and turns the screen on, mirroring a real call. All actions are sent to
 * [IncomingCallService]; this activity is pure UI and can be dismissed without ending the call.
 */
@AndroidEntryPoint
class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)

        setContent {
            SmartReminderTheme(dynamicColor = false) {
                val state by CallStateHolder.state.collectAsState()
                CallScreen(
                    state = state,
                    onAnswer = { IncomingCallService.sendAction(this, IncomingCallService.ACTION_ANSWER, reminderId) },
                    onDecline = {
                        IncomingCallService.sendAction(this, IncomingCallService.ACTION_DECLINE, reminderId)
                        finish()
                    },
                    onSnooze = {
                        IncomingCallService.sendAction(this, IncomingCallService.ACTION_SNOOZE, reminderId)
                        finish()
                    },
                    onSpeaker = { IncomingCallService.sendAction(this, IncomingCallService.ACTION_SPEAKER, reminderId) },
                    onEnd = {
                        IncomingCallService.sendAction(this, IncomingCallService.ACTION_END, reminderId)
                        finish()
                    },
                    onFinished = { finish() },
                )
            }
        }
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
