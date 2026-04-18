package com.mari.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                val navController = rememberSwipeDismissableNavController()
                WearNavGraph(navController)
            }
        }
    }
}
