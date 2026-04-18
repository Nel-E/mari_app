package com.mari.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mari.wear.shake.ShakeLifecycleObserver
import com.mari.wear.ui.nav.WearNavGraph
import com.mari.wear.ui.theme.WearMariTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var shakeLifecycleObserver: ShakeLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(shakeLifecycleObserver)
        setContent {
            WearMariTheme {
                Scaffold(
                    timeText = { TimeText() },
                    vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
                ) { _ ->
                    val navController = rememberSwipeDismissableNavController()
                    WearNavGraph(navController)
                }
            }
        }
    }
}
