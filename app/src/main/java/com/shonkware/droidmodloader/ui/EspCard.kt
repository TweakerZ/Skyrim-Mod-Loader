package com.shonkware.droidmodloader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shonkware.droidmodloader.engine.index.ModContentEntry
import com.shonkware.droidmodloader.engine.model.PluginEntry
import com.shonkware.droidmodloader.ui.theme.DmlButtons
import com.shonkware.droidmodloader.ui.theme.DmlColors
import com.shonkware.droidmodloader.ui.theme.DmlDefaults

/**
 * Editable ESP / plugin inventory for the Skyrim Android static port.
 *
 * Plugins are shown with an enable/disable tick and a priority number, like the
 * Mods card. Reordering (Up / Down) and toggling are kept as pending changes and
 * only written to the port folder's Plugins.txt / loadorder.txt when "Save ESP
 * Order" is tapped. Nothing is written to the port folder until then.
 */
@Composable
fun EspCard(
    plugins: List<PluginEntry>,
    archives: List<ModContentEntry>,
    onTogglePlugin: (String) -> Unit,
    onMovePluginUp: (String) -> Unit,
    onMovePluginDown: (String) -> Unit,
    onSaveEspOrder: () -> Unit,
    hasPendingChanges: Boolean = false
) {
    // Keep the caller's order exactly. The pending list is already in display
    // order, so sorting by priority here would undo live Up/Down moves.
    val orderedPlugins = plugins

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = DmlDefaults.panelCardColors(),
        border = BorderStroke(1.dp, DmlColors.BorderDim)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ESP/ESM/ESL plugins. Tick to enable/disable; use Up/Down to reorder. " +
                    "Changes apply when you tap Save Order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (orderedPlugins.isEmpty()) {
                Text("No plugins found.", style = MaterialTheme.typography.bodySmall)
            } else {
                orderedPlugins.withIndex().forEach { (index, plugin) ->
                    CompactPluginRow(
                        plugin = plugin,
                        position = index,
                        canMoveUp = index > 0 && !plugin.locked && !orderedPlugins[index - 1].locked,
                        canMoveDown = index < orderedPlugins.lastIndex && !plugin.locked &&
                            !orderedPlugins[index + 1].locked,
                        onTogglePlugin = onTogglePlugin,
                        onMovePluginUp = onMovePluginUp,
                        onMovePluginDown = onMovePluginDown
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

            Spacer(Modifier.height(4.dp))

            DmlButtons.Primary(
                text = "Save ESP Order",
                onClick = onSaveEspOrder,
                enabled = hasPendingChanges,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CompactPluginRow(
    plugin: PluginEntry,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTogglePlugin: (String) -> Unit,
    onMovePluginUp: (String) -> Unit,
    onMovePluginDown: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = DmlDefaults.raisedCardColors(),
        border = BorderStroke(1.dp, DmlColors.BorderDim)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = plugin.enabled,
                    onCheckedChange = {
                        if (!plugin.locked) onTogglePlugin(plugin.normalizedPath)
                    },
                    enabled = !plugin.locked
                )

                Text(
                    text = (position + 1).toString().padStart(3, '0'),
                    fontWeight = FontWeight.Bold
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.pluginName,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = plugin.pluginType + (if (plugin.locked) " (locked)" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "More")
                }
            }

            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DmlButtons.Secondary(
                        text = "Up",
                        onClick = { onMovePluginUp(plugin.normalizedPath) },
                        enabled = canMoveUp
                    )

                    DmlButtons.Secondary(
                        text = "Down",
                        onClick = { onMovePluginDown(plugin.normalizedPath) },
                        enabled = canMoveDown
                    )
                }
            }
        }
    }
}
