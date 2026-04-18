package com.mari.wear.di

import android.app.AlarmManager
import android.content.Context
import com.mari.wear.reminders.AlarmReminderScheduler
import com.mari.wear.reminders.ReminderScheduler
import com.mari.wear.shake.ShakeConfig
import com.mari.wear.shake.ShakeDetector
import com.mari.wear.shake.ShakeEventSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindShakeEventSource(impl: ShakeDetector): ShakeEventSource

    companion object {
        @Provides
        @Singleton
        fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
            context.getSystemService(AlarmManager::class.java)

        @Provides
        @Singleton
        fun provideShakeConfig(): ShakeConfig = ShakeConfig()

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
