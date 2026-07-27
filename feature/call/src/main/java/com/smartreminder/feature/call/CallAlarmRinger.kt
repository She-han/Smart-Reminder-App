package com.smartreminder.feature.call

import android.content.Context
import com.smartreminder.core.alarm.AlarmRinger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real alarm handler: when a reminder fires, start the incoming-call service so it rings
 * like a phone call. Replaces the Phase 6 notification-only ringer.
 */
@Singleton
class CallAlarmRinger @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmRinger {
    override fun ring(reminderId: Long) {
        IncomingCallService.start(context, reminderId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CallRingerModule {
    @Binds
    abstract fun bindAlarmRinger(impl: CallAlarmRinger): AlarmRinger
}
