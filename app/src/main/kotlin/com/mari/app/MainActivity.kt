package com.mari.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mari.app.settings.SettingsReader
import com.mari.app.settings.ThemeMode
import com.mari.app.shake.ShakeLifecycleObserver
import com.mari.app.ui.nav.MariNavGraph
import com.mari.app.ui.theme.MariTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shakeLifecycleObserver: ShakeLifecycleObserver

    @Inject
    lateinit var settingsReader: SettingsReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(shakeLifecycleObserver)
        setContent {
            val themeMode by settingsReader.settings.map { it.themeMode }
                .collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            MariTheme(darkTheme = darkTheme) {
                MariNavGraph()
            }
        }
    }
}
