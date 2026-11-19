package com.shonkware.droidmodloader.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shonkware.droidmodloader.engine.model.GameProfile
import com.shonkware.droidmodloader.ui.theme.DmlButtons
import com.shonkware.droidmodloader.ui.theme.DmlColors
import com.shonkware.droidmodloader.ui.theme.DmlDefaults
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
        title = "Skyrim Mod Loader",
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
                supportingText = "SML checks the folder and finds its Data folder automatically."
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
    var profilePendingDelete by remember { mutableStateOf<GameProfile?>(null) }

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
                        ProfileCard(
                            profile = profile,
                            isActive = profile.profileId == activeProfileId,
                            onSelectProfile = onSelectProfile,
                            onRequestDelete = { profilePendingDelete = it }
                        )
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

    profilePendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profilePendingDelete = null },
            title = {
                Text("Delete profile?")
            },
            text = {
                Text(
                    "Delete \"${profile.profileName}\" and every mod installed in it? " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        profilePendingDelete = null
                        onDeleteProfile(profile.profileId)
                    }
                ) {
                    Text("Delete profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: GameProfile,
    isActive: Boolean,
    onSelectProfile: (String) -> Unit,
    onRequestDelete: (GameProfile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val gameName = profile.gameDisplayName.ifBlank {
        GameCatalog.displayName(profile.gameId)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = DmlDefaults.raisedCardColors(),
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else DmlColors.BorderDim
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = profile.profileName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "More")
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Game: $gameName",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Game folder: ${profile.targetRootPath.ifBlank { "Not selected" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Data folder: ${profile.targetDataPath.ifBlank { "Not selected" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Deployment: ${
                            if (profile.realDeployEnabled) "Direct" else "Simulated"
                        }",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (isActive) {
                        Text(
                            text = "Active profile. Switch to another profile to delete it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        DmlButtons.Secondary(
                            text = "Switch",
                            onClick = { onSelectProfile(profile.profileId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DmlButtons.Danger(
                            text = "Delete",
                            onClick = { onRequestDelete(profile) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
