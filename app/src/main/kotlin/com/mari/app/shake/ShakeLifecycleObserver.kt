package com.mari.app.shake

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mari.app.di.ApplicationScope
import com.mari.app.settings.SettingsRepository
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@Singleton
class ShakeLifecycleObserver @Inject constructor(
    private val detector: ShakeDetector,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private var resumed = false
    private var latestConfig = ShakeConfig()

    init {
        settingsRepository.settings
            .onEach { settings ->
                latestConfig = settings.shakeConfig
                if (resumed) detector.start(latestConfig)
            }
            .launchIn(scope)
    }

    override fun onResume(owner: LifecycleOwner) {
        resumed = true
        detector.start(latestConfig)
    }

    override fun onPause(owner: LifecycleOwner) {
        resumed = false
        detector.stop()
    }
}
