package com.mari.wear

import android.app.Application
import com.mari.wear.data.repository.WatchTaskRepository
import com.mari.wear.di.ApplicationScope
import com.mari.wear.reminders.ExecutingStatusObserver
import com.mari.wear.sync.WatchSyncClient
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MariWearApp : Application() {

    @Inject lateinit var watchTaskRepository: WatchTaskRepository
    @Inject lateinit var executingStatusObserver: ExecutingStatusObserver
    @Inject lateinit var syncClient: WatchSyncClient
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch { watchTaskRepository.init() }
        executingStatusObserver.start()
        syncClient.start()
    }
}
