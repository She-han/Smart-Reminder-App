package com.smartreminder.core.audio.di

import com.smartreminder.core.audio.VoiceRecorder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    /**
     * The recorder holds no long-lived state between sessions and owns a hardware stream only
     * while active, so a fresh instance per injection point is intentional.
     */
    @Provides
    fun provideVoiceRecorder(): VoiceRecorder = VoiceRecorder()
}
