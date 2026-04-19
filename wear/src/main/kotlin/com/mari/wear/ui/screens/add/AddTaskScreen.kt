package com.mari.wear.ui.screens.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper

private const val KEY_DESCRIPTION = "description"

@Composable
fun AddTaskScreen(
    navController: NavController,
    viewModel: AddTaskViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val inputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val bundle = android.app.RemoteInput.getResultsFromIntent(result.data ?: return@rememberLauncherForActivityResult)
        val text = bundle?.getCharSequence(KEY_DESCRIPTION)?.toString()
        if (!text.isNullOrBlank()) {
            viewModel.onDescriptionChange(text)
            viewModel.save()
        }
    }

    fun launchInput() {
        val remoteInput = android.app.RemoteInput.Builder(KEY_DESCRIPTION)
            .setLabel("Describe the task")
            .build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        inputLauncher.launch(intent)
    }

    LaunchedEffect(Unit) { launchInput() }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) navController.popBackStack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            uiState.descriptionError != null -> {
                Text(
                    text = uiState.descriptionError!!,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = ::launchInput,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Try Again")
                }
            }
            uiState.isSaving -> Text("Saving…")
            uiState.description.isNotEmpty() -> Text("Saving…")
            else -> Text("Opening keyboard…")
        }
    }
}
