package com.shonkware.droidmodloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shonkware.droidmodloader.ui.theme.DmlButtons
import com.skyrimsuite.ui.SuitePathRow
import com.skyrimsuite.ui.SuiteSectionHeader

/**
 * Contents of the advanced-settings sheet. Every filesystem path in the app
 * lives here; the dashboard itself only shows readable status chips.
 */
@Composable
fun DeploymentSettingsCard(
    selectedDataPathText: String,
    selectedRootPathText: String,
    portFolderPath: String,
    portFolderValidationMessage: String?,
    onPickTargetFolder: () -> Unit,
    onSaveSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SuiteSectionHeader(
            title = "Game folders",
            subtitle = "SML checks the game folder and finds its Data folder automatically."
        )

        SuitePathRow(
            title = "Game folder",
            path = selectedRootPathText,
            onChange = onPickTargetFolder
        )

        SuitePathRow(
            title = "Data folder (detected)",
            path = selectedDataPathText,
            onChange = onPickTargetFolder
        )

        SuitePathRow(
            title = "Port folder",
            path = portFolderPath,
            onChange = onPickTargetFolder
        )

        if (portFolderValidationMessage != null) {
            Text(
                text = portFolderValidationMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        DmlButtons.Primary(
            text = "Save Settings",
            onClick = onSaveSettings,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
