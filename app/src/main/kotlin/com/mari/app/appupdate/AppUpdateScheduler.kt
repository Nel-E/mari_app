package com.mari.app.appupdate

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mari.app.domain.model.UpdateTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueuePeriodic(intervalHours: Int = 6) {
        val clamped = intervalHours.coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(clamped.toLong(), TimeUnit.HOURS)
            .setConstraints(networkConstraints)
            .setInputData(Data.Builder().putBoolean(AppUpdateCheckWorker.KEY_FORCE_NOTIFY, false).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            AppUpdateCheckWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueOnStartup() {
        val request = OneTimeWorkRequestBuilder<AppUpdateCheckWorker>()
            .setConstraints(networkConstraints)
            .setInputData(Data.Builder().putBoolean(AppUpdateCheckWorker.KEY_FORCE_NOTIFY, false).build())
            .build()
        workManager.enqueueUniqueWork(
            AppUpdateCheckWorker.WORK_NAME_STARTUP,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueueManual() {
        val request = OneTimeWorkRequestBuilder<AppUpdateCheckWorker>()
            .setConstraints(networkConstraints)
            .setInputData(Data.Builder().putBoolean(AppUpdateCheckWorker.KEY_FORCE_NOTIFY, true).build())
            .build()
        workManager.enqueueUniqueWork(
            AppUpdateCheckWorker.WORK_NAME_MANUAL,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun reenqueuePeriodic(newIntervalHours: Int) = enqueuePeriodic(newIntervalHours)

    @Suppress("UnusedParameter")
    fun reenqueueOnTrackChange(newTrack: UpdateTrack) {
        workManager.cancelUniqueWork(AppUpdateCheckWorker.WORK_NAME_PERIODIC)
        enqueuePeriodic()
        enqueueManual()
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(AppUpdateCheckWorker.WORK_NAME_PERIODIC)
        workManager.cancelUniqueWork(AppUpdateCheckWorker.WORK_NAME_STARTUP)
        workManager.cancelUniqueWork(AppUpdateCheckWorker.WORK_NAME_MANUAL)
    }
}
