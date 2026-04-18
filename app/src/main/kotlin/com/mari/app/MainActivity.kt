package com.mari.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mari.app.shake.ShakeLifecycleObserver
import com.mari.app.ui.nav.MariNavGraph
import com.mari.app.ui.theme.MariTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shakeLifecycleObserver: ShakeLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(shakeLifecycleObserver)
        setContent {
            MariTheme {
                MariNavGraph()
            }
        }
    }
}
