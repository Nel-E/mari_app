package com.mari.wear.ui.screens.add

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text

@Composable
fun AddTaskScreen(
    navController: NavController,
    viewModel: AddTaskViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = matches?.firstOrNull()
        when {
            text != null -> viewModel.onVoiceResult(text)
            result.resultCode == Activity.RESULT_CANCELED -> viewModel.onVoiceCancelled()
            else -> viewModel.onVoiceEmpty()
        }
    }

    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the task")
        }
        voiceLauncher.launch(intent)
    }

    LaunchedEffect(Unit) { launchVoice() }

    if (uiState.saved) {
        LaunchedEffect(Unit) { navController.popBackStack() }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            uiState.voiceError != null -> {
                Text(uiState.voiceError!!)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    viewModel.clearError()
                    launchVoice()
                }) {
                    Text("Try Again")
                }
            }
            uiState.description.isNotEmpty() -> Text("Saving…")
            else -> Text("Listening…")
        }
    }
}
