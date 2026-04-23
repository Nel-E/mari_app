package com.mari.app.di

import com.mari.app.data.repository.AppUpdateRepositoryImpl
import com.mari.app.domain.repository.AppUpdateRepository
import com.mari.app.wearinstall.WearApkDispatcher
import com.mari.app.wearinstall.WearUpdatePusher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(impl: AppUpdateRepositoryImpl): AppUpdateRepository

    @Binds
    @Singleton
    abstract fun bindWearUpdatePusher(impl: WearApkDispatcher): WearUpdatePusher
}
