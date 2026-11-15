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
import com.skyrimsuite.ui.SuiteCard
import com.skyrimsuite.ui.SuiteSectionHeader

/**
 * Compact "what just happened" strip. Everything it used to repeat (profile,
 * game, deploy target, data/root readiness) is now a chip in the header, so
 * only the live operation status and summary remain here.
 */
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
    SuiteCard {
        Text(
            text = "Status: $lastOperationStatus",
            style = MaterialTheme.typography.titleSmall
        )
        if (summaryText.isNotBlank()) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DmlButtons.Secondary(
            text = "Manage Profiles",
            onClick = onOpenProfileDialog,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * The old header, status and "install & deploy" cards are gone.
 *
 * * the app title now lives in the centered top app bar,
 * * deployment status is a row of chips under the bar,
 * * import/deploy actions are the pinned pill buttons at the bottom.
 *
 * What remains here is the short in-app help, which lives in the advanced
 * settings sheet so it does not push the working screen down.
 */
@Composable
fun QuickStartCard() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SuiteSectionHeader(
            title = "Quick start",
            subtitle = "The short version of the usual workflow."
        )

        SuiteCard {
            Text(
                text = "1. Choose the installed Skyrim folder once.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "2. Tap Import Mods and pick one or more zip, 7z or rar files.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "3. Review the extracted mods and choose the files you want.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "4. Check the read-only ESP/BSA inventory.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "5. Tap Deploy Mods to write the enabled mod files.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "6. Share logs if something looks wrong.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Small inset note used at the bottom of the settings sheet. */
@Composable
fun SettingsFootnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}
