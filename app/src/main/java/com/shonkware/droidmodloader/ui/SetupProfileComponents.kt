package com.shonkware.droidmodloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shonkware.droidmodloader.engine.model.GameProfile
import com.shonkware.droidmodloader.ui.workflow.GameCatalog
import com.skyrimsuite.ui.SuiteBarAction
import com.skyrimsuite.ui.SuiteCard
import com.skyrimsuite.ui.SuiteChip
import com.skyrimsuite.ui.SuiteChipTone
import com.skyrimsuite.ui.SuitePrimaryButton
import com.skyrimsuite.ui.SuiteScreen
import com.skyrimsuite.ui.SuiteTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameOptionChips(
    gameOptions: List<String>,
    selectedGameId: String,
    onSelectGame: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        gameOptions.chunked(2).forEach { rowGames ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowGames.forEach { gameId ->
                    FilterChip(
                        selected = selectedGameId == gameId,
                        onClick = { onSelectGame(gameId) },
                        modifier = Modifier.weight(1f),
                        label = { Text(GameCatalog.displayName(gameId)) }
                    )
                }
            }
        }
    }
}

@Composable
fun SetupScreen(
    state: DashboardUiState,
    actions: DashboardActions
) {
    SuiteScreen(
        title = "Droid Mod Loader",
        subtitle = "First setup",
        chips = listOf(
            SuiteChip("No profile yet", SuiteChipTone.Warning),
            SuiteChip(GameCatalog.displayName(state.setupGameId))
        ),
        primaryAction = SuiteBarAction(
            label = "Create Profile",
            onClick = actions.onCompleteSetup,
            enabled = state.setupInstallationResolved
        )
    ) {
        SuiteCard {
            Text(
                text = "Create your first game profile",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "A profile keeps one game installation, its mods and its load order together.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SuiteTextField(
                value = state.profileNameText,
                onValueChange = actions.onProfileNameChanged,
                label = "Profile name"
            )

            Text(
                text = "Game",
                style = MaterialTheme.typography.titleSmall
            )

            GameOptionChips(
                gameOptions = state.gameOptions,
                selectedGameId = state.setupGameId,
                onSelectGame = actions.onSetupGameChanged
            )
        }

        SuiteCard {
            Text(
                text = "Game folder",
                style = MaterialTheme.typography.titleLarge
            )

            com.skyrimsuite.ui.SuitePathRow(
                title = "Installed game folder",
                path = state.setupRootTargetPathText,
                onChange = actions.onPickTargetFolder,
                supportingText = "DML checks the folder and finds its Data folder automatically."
            )

            com.skyrimsuite.ui.SuitePathRow(
                title = "Data folder (detected)",
                path = state.setupTargetPathText
            )

            Text(
                text = if (state.setupInstallationResolved) {
                    "Game installation validated."
                } else {
                    "Select a valid game folder to continue."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.setupInstallationResolved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun ProfileManagerDialog(
    profiles: List<GameProfile>,
    activeProfileId: String?,
    newProfileNameText: String,
    newProfileGameId: String,
    newProfileDataPathText: String,
    newProfileInstallationResolved: Boolean,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onNewProfileNameChanged: (String) -> Unit,
    onNewProfileGameChanged: (String) -> Unit,
    onPickNewProfileTargetFolder: () -> Unit,
    onCreateAdditionalProfile: () -> Unit,
    onClose: () -> Unit,
    gameOptions: List<String>,
    newProfileRootPathText: String = ""
) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
        title = {
            Text("Manage Profiles")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Switch Profile", style = MaterialTheme.typography.titleLarge)

                if (profiles.isEmpty()) {
                    Text("No profiles found.")
                } else {
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = profile.profileId == activeProfileId,
                                onClick = { onSelectProfile(profile.profileId) },
                                label = { Text(profile.profileName) }
                            )

                            TextButton(onClick = { onDeleteProfile(profile.profileId) }) {
                                Text("Delete")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text("Add Profile", style = MaterialTheme.typography.titleLarge)

                SuiteTextField(
                    value = newProfileNameText,
                    onValueChange = onNewProfileNameChanged,
                    label = "Profile name"
                )

                GameOptionChips(
                    gameOptions = gameOptions,
                    selectedGameId = newProfileGameId,
                    onSelectGame = onNewProfileGameChanged
                )

                com.skyrimsuite.ui.SuitePathRow(
                    title = "Game folder",
                    path = newProfileRootPathText,
                    onChange = onPickNewProfileTargetFolder
                )

                com.skyrimsuite.ui.SuitePathRow(
                    title = "Data folder (detected)",
                    path = newProfileDataPathText
                )

                Text(
                    text = if (newProfileInstallationResolved) {
                        "Game installation validated."
                    } else {
                        "Select a valid game folder to continue."
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                SuitePrimaryButton(
                    text = "Create Profile",
                    onClick = onCreateAdditionalProfile,
                    enabled = newProfileInstallationResolved,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    )
}
