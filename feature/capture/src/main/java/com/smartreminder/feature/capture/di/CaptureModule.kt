package com.smartreminder.feature.capture.di

import com.smartreminder.core.nlp.DateTimeEntityExtractor
import com.smartreminder.core.nlp.ReminderParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {

    /**
     * The tested offline regex grammar is the primary (and currently only) extraction path;
     * a platform TextClassifier extractor can be swapped in here later as a supplementary
     * fallback without touching the parser or its callers.
     */
    @Provides
    @Singleton
    fun provideReminderParser(clock: Clock): ReminderParser =
        ReminderParser(
            extractor = DateTimeEntityExtractor.NONE,
            clock = clock,
            zone = ZoneId.systemDefault(),
        )
}
