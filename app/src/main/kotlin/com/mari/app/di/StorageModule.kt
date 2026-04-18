package com.mari.app.di

import com.mari.app.data.repository.FileTaskRepository
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: FileTaskRepository): TaskRepository

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = SystemClock
    }
}
