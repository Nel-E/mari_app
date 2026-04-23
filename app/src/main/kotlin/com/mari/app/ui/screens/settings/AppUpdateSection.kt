package com.mari.app.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mari.app.domain.model.AppUpdateInfo
import com.mari.app.domain.model.UpdateTrack

@Composable
internal fun AppUpdateSection(
    autoCheckEnabled: Boolean,
    track: UpdateTrack,
    availableUpdate: AppUpdateInfo?,
    onAutoCheckChange: (Boolean) -> Unit,
    onTrackChange: (UpdateTrack) -> Unit,
    onCheckNow: () -> Unit,
    onViewUpdate: () -> Unit,
) {
    HorizontalDivider()
    Text(
        text = "App Updates",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    ListItem(
        headlineContent = { Text("Auto-check for updates") },
        trailingContent = {
            Switch(checked = autoCheckEnabled, onCheckedChange = onAutoCheckChange)
        },
    )
    ListItem(
        headlineContent = { Text("Channel") },
        supportingContent = { Text(if (track == UpdateTrack.BETA) "Beta" else "Stable") },
        trailingContent = {
            TextButton(onClick = {
                onTrackChange(if (track == UpdateTrack.STABLE) UpdateTrack.BETA else UpdateTrack.STABLE)
            }) {
                Text(if (track == UpdateTrack.STABLE) "Switch to Beta" else "Switch to Stable")
            }
        },
    )
    ListItem(
        headlineContent = { Text("Check now") },
        trailingContent = { TextButton(onClick = onCheckNow) { Text("Check") } },
    )
    if (availableUpdate != null) {
        ListItem(
            headlineContent = { Text("Update available") },
            supportingContent = { Text("Version ${availableUpdate.versionName}") },
            trailingContent = { TextButton(onClick = onViewUpdate) { Text("View") } },
        )
    }
}
