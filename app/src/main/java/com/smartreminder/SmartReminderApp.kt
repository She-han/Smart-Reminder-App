package com.smartreminder

import android.app.Application
import com.smartreminder.feature.call.CallNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CallNotifications.registerChannels(this)
    }
}
