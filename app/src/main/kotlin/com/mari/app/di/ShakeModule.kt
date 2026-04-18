package com.mari.app.di

import android.content.Context
import android.hardware.SensorManager
import com.mari.app.shake.ShakeConfig
import com.mari.app.shake.ShakeDetector
import com.mari.app.shake.ShakeEventSource
import com.mari.app.shake.ShakeFeedback
import com.mari.app.shake.ShakeFeedbackImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ShakeModule {

    @Binds
    @Singleton
    abstract fun bindShakeEventSource(impl: ShakeDetector): ShakeEventSource

    @Binds
    @Singleton
    abstract fun bindShakeFeedback(impl: ShakeFeedbackImpl): ShakeFeedback

    companion object {
        @Provides
        @Singleton
        fun provideSensorManager(@ApplicationContext context: Context): SensorManager =
            context.getSystemService(SensorManager::class.java)

        @Provides
        @Singleton
        fun provideShakeConfig(): ShakeConfig = ShakeConfig()
    }
}
