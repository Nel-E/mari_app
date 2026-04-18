package com.mari.wear.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text

@Composable
fun SettingsScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompactChip(
            label = { Text(if (uiState.reminderEnabled) "Reminders on" else "Reminders off") },
            onClick = viewModel::toggleReminderEnabled,
        )
        Spacer(Modifier.height(8.dp))
        Text("Reminder interval", textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactButton(onClick = viewModel::decrementReminderInterval) {
                Text("-")
            }
            Spacer(Modifier.width(8.dp))
            Text("${uiState.reminderIntervalMinutes} min")
            Spacer(Modifier.width(8.dp))
            CompactButton(onClick = viewModel::incrementReminderInterval) {
                Text("+")
            }
        }
    }
}
