package com.mari.app.domain.repository

import com.mari.app.domain.model.AppUpdateLocalState
import com.mari.app.domain.model.UpdateTrack
import kotlinx.coroutines.flow.StateFlow

interface AppUpdateRepository {
    val state: StateFlow<AppUpdateLocalState>
    suspend fun checkForUpdate(forceNotify: Boolean = false)
    suspend fun setAutoCheckEnabled(enabled: Boolean)
    suspend fun setTrack(track: UpdateTrack)
    suspend fun setCheckInterval(hours: Int)
    suspend fun acknowledgeUpdate(versionCode: Int)
    suspend fun clearAvailableUpdate()
    suspend fun setLastNotifiedVersionCode(code: Int?)
}
