package com.mari.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mari.app.appupdate.AppUpdateScheduler
import com.mari.app.data.repository.FileTaskRepository
import com.mari.app.di.ApplicationScope
import com.mari.app.reminders.ExecutingStatusObserver
import com.mari.app.sync.PhoneSyncClient
import com.mari.app.wearinstall.WearApkDispatcher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MariApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var executingStatusObserver: ExecutingStatusObserver

    @Inject
    lateinit var taskRepository: FileTaskRepository

    @Inject
    lateinit var syncClient: PhoneSyncClient

    @Inject
    lateinit var wearApkDispatcher: WearApkDispatcher

    @Inject
    lateinit var appUpdateScheduler: AppUpdateScheduler

    @Inject
    @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { taskRepository.init() }
        executingStatusObserver.start()
        syncClient.start()
        appScope.launch { wearApkDispatcher.dispatchIfAvailable() }
        appUpdateScheduler.enqueuePeriodic()
        appUpdateScheduler.enqueueOnStartup()
    }
}
