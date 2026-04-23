package com.mari.app.appupdate

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mari.app.domain.repository.AppUpdateRepository
import com.mari.app.wearinstall.WearUpdatePusher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AppUpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AppUpdateRepository,
    private val notifier: AppUpdateNotifier,
    private val wearApkDispatcher: WearUpdatePusher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val forceNotify = inputData.getBoolean(KEY_FORCE_NOTIFY, false)
        val state = repository.state.value
        if (!forceNotify && !state.autoCheckEnabled) return Result.success()

        repository.checkForUpdate(forceNotify)

        val updated = repository.state.value
        val available = updated.availableUpdate
        if (available != null) {
            val alreadyNotified = updated.lastNotifiedVersionCode == available.versionCode && !forceNotify
            if (!alreadyNotified) {
                notifier.notifyAvailable(available)
            }
        }

        wearApkDispatcher.pushServerUpdateIfNeeded(updated)

        return Result.success()
    }

    companion object {
        const val KEY_FORCE_NOTIFY = "force_notify"
        const val WORK_NAME_PERIODIC = "app_update_periodic"
        const val WORK_NAME_STARTUP = "app_update_startup"
        const val WORK_NAME_MANUAL = "app_update_manual"
    }
}
