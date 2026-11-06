package com.shonkware.droidmodloader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shonkware.droidmodloader.engine.index.ModContentEntry
import com.shonkware.droidmodloader.engine.model.PluginEntry
import com.shonkware.droidmodloader.ui.theme.DmlColors
import com.shonkware.droidmodloader.ui.theme.DmlDefaults

/**
 * Read-only inventory of plugins and BSA/BA2 archives. The Skyrim Android static port
 * owns load order and plugin activation, so this screen never writes either.
 */
@Composable
fun PluginsAndArchivesCard(
    plugins: List<PluginEntry>,
    archives: List<ModContentEntry>
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
            Text("Plugins & Archives", fontWeight = FontWeight.Bold)
            Text(
                text = "Read-only inventory. ESP/ESM/ESL and BSA/BA2 files are listed only; " +
                    "the Skyrim Android port manages its own load order.",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Plugins (${plugins.size})",
                fontWeight = FontWeight.SemiBold
            )

            if (plugins.isEmpty()) {
                Text("No plugins found.", style = MaterialTheme.typography.bodySmall)
            } else {
                plugins.forEach { plugin ->
                    Text(
                        text = plugin.pluginName,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Archives (${archives.size})",
                fontWeight = FontWeight.SemiBold
            )

            if (archives.isEmpty()) {
                Text("No BSA/BA2 archives found.", style = MaterialTheme.typography.bodySmall)
            } else {
                archives.forEach { archive ->
                    Text(
                        text = archive.normalizedPath,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
