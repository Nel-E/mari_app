package com.mari.app.di

import android.app.AlarmManager
import android.content.Context
import androidx.work.WorkManager
import com.mari.app.reminders.AlarmReminderScheduler
import com.mari.app.reminders.AlarmDeadlineReminderScheduler
import com.mari.app.reminders.DeadlineReminderScheduler
import com.mari.app.reminders.ReminderRouter
import com.mari.app.reminders.ReminderScheduler
import com.mari.app.reminders.WorkManagerReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AlarmScheduler

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WorkScheduler

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(router: ReminderRouter): ReminderScheduler

    @Binds
    @Singleton
    @AlarmScheduler
    abstract fun bindAlarmScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindDeadlineReminderScheduler(impl: AlarmDeadlineReminderScheduler): DeadlineReminderScheduler

    @Binds
    @Singleton
    @WorkScheduler
    abstract fun bindWorkManagerScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    companion object {
        @Provides
        @Singleton
        fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
            context.getSystemService(AlarmManager::class.java)

        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
