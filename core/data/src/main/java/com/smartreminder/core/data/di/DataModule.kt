package com.smartreminder.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.smartreminder.core.data.db.ReminderDao
import com.smartreminder.core.data.db.ReminderDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReminderDatabase =
        Room.databaseBuilder(context, ReminderDatabase::class.java, ReminderDatabase.NAME).build()

    @Provides
    fun provideReminderDao(db: ReminderDatabase): ReminderDao = db.reminderDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("settings") },
        )

    /**
     * Injected everywhere instead of calling [Instant.now] directly, so time-dependent
     * logic (parser, redial scheduling) is testable with a fixed clock.
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
