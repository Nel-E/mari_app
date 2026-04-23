package com.mari.app.wearinstall

import com.mari.app.domain.model.AppUpdateLocalState

interface WearUpdatePusher {
    suspend fun pushServerUpdateIfNeeded(state: AppUpdateLocalState)
}
