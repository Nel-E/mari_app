package com.mari.wear.di

import android.content.Context
import android.hardware.SensorManager
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.SystemClock
import com.mari.wear.data.cache.TaskCacheStorage
import com.mari.wear.data.cache.WatchCacheStorage
import com.mari.wear.data.repository.WatchTaskRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: WatchTaskRepository): TaskRepository

    @Binds
    @Singleton
    abstract fun bindTaskCacheStorage(impl: WatchCacheStorage): TaskCacheStorage

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = SystemClock

        @Provides
        @Singleton
        fun provideSensorManager(@ApplicationContext context: Context): SensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
}
