package com.shonkware.droidmodloader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shonkware.droidmodloader.ui.theme.DmlButtons
import com.shonkware.droidmodloader.ui.theme.DmlColors
import com.shonkware.droidmodloader.ui.theme.DmlDefaults
import com.shonkware.droidmodloader.ui.workflow.GameCatalog


@Composable
fun HeaderCard(
    appName: String,
    versionLabel: String,
    onVersionTap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = DmlDefaults.panelCardColors(),
        border = BorderStroke(1.dp, DmlColors.BorderDim)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = versionLabel,
                    modifier = Modifier.clickable { onVersionTap() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusCard(
    activeProfileName: String,
    selectedGameId: String,
    dataTargetReady: Boolean,
    rootTargetReady: Boolean,
    realDeployEnabled: Boolean,
    lastOperationStatus: String,
    summaryText: String,
    onOpenProfileDialog: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = DmlDefaults.panelCardColors(),
        border = BorderStroke(1.dp, DmlColors.BorderDim)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Profile: $activeProfileName",
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Game: ${GameCatalog.displayName(selectedGameId)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                DmlButtons.Secondary(
                    text = "Manage Profiles",
                    onClick = onOpenProfileDialog
                )
            }

            Text(
                text = if (realDeployEnabled) {
                    "Deploy to: Game folders"
                } else {
                    "Deploy to: Test folders"
                },
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = if (dataTargetReady) {
                    "Data folder: selected"
                } else {
                    "Data folder: not selected"
                },
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = if (rootTargetReady) {
                    "Game Root folder: selected"
                } else {
                    "Game Root folder: not selected"
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (!rootTargetReady) {
                Text(
                    text = "Choose the game's main folder so DML can check it and find its Data folder.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("Status: $lastOperationStatus")

            if (summaryText.isNotBlank()) {
                Text(summaryText)
            }
        }
    }
}

@Composable
fun QuickStartCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = DmlDefaults.panelCardColors(),
        border = BorderStroke(1.dp, DmlColors.BorderDim)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Quick Start", fontWeight = FontWeight.Bold)

            Text("1. Choose the installed Skyrim folder once.")
            Text("2. Tap Import Mod Archives and pick one or more zip, 7z or rar files.")
            Text("3. Review the extracted mods and choose the files you want.")
            Text("4. Check the read-only ESP/BSA inventory.")
            Text("5. Choose Deploy Mods to write the enabled mod files.")
            Text("6. Share logs if something looks wrong.")
        }
    }
}

@Composable
fun MainActionsCard(
    operationInProgress: Boolean,
    archiveImportInProgress: Boolean,
    archiveQueueCount: Int,
    onInstallMod: () -> Unit,
    onDeployMods: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = DmlDefaults.panelCardColors(),
        border = BorderStroke(1.dp, DmlColors.BorderHot)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Install & Deploy", fontWeight = FontWeight.Bold)

            DmlButtons.Primary(
                text = if (archiveImportInProgress) "Importing archive..." else "Import Mod Archives",
                enabled = !operationInProgress && !archiveImportInProgress,
                onClick = onInstallMod,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Pick one or more .zip, .7z or .rar files with the Android file picker. " +
                    "Archives are extracted straight into mod storage and no zip copy is kept.",
                style = MaterialTheme.typography.bodySmall
            )

            if (archiveQueueCount > 0) {
                Text(
                    text = "$archiveQueueCount archive(s) waiting in the import queue.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            DmlButtons.Primary(
                text = "Deploy Mods",
                enabled = !operationInProgress,
                onClick = onDeployMods,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}