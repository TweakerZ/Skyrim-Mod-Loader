package com.shonkware.droidmodloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shonkware.droidmodloader.engine.flags.ModFlag
import com.shonkware.droidmodloader.engine.index.ModContentIndex
import com.shonkware.droidmodloader.engine.model.Mod
import com.shonkware.droidmodloader.engine.model.PluginEntry

@Composable
fun ModsPanelDialog(
    mods: List<Mod>,
    modContentIndexes: Map<String, ModContentIndex>,
    modFlags: Map<String, Set<ModFlag>>,
    onToggleMod: (String) -> Unit,
    onMoveModUp: (String) -> Unit,
    onMoveModDown: (String) -> Unit,
    onMoveModToPosition: (String, Int) -> Unit,
    onDeleteMod: (Mod) -> Unit,
    onViewModFiles: (String) -> Unit,
    onOpenOverwriteFolder: () -> Unit,
    onClose: () -> Unit,
    listState: LazyListState
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.94f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mods",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenOverwriteFolder) {
                            Text("Overwrite")
                        }

                        Button(onClick = onClose) {
                            Text("Close")
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = mods,
                        key = { _, mod -> mod.id }
                    ) { _, mod ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CompactModRow(
                                    mod = mod,
                                    contentIndex = modContentIndexes[mod.id],
                                    flags = modFlags[mod.id].orEmpty(),
                                    maxPosition = mods.size,
                                    onToggleMod = onToggleMod,
                                    onMoveModUp = onMoveModUp,
                                    onMoveModDown = onMoveModDown,
                                    onMoveModToPosition = onMoveModToPosition,
                                    onDeleteMod = onDeleteMod,
                                    onViewModFiles = onViewModFiles
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PluginsPanelDialog(
    plugins: List<PluginEntry>,
    onTogglePlugin: (String) -> Unit,
    onMovePluginUp: (String) -> Unit,
    onMovePluginDown: (String) -> Unit,
    onClose: () -> Unit,
    listState: LazyListState
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.94f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Plugins",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Button(onClick = onClose) {
                        Text("Close")
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = plugins,
                        key = { _, plugin -> plugin.normalizedPath }
                    ) { _, plugin ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                PluginRow(
                                    plugin = plugin,
                                    onTogglePlugin = onTogglePlugin,
                                    onMovePluginUp = onMovePluginUp,
                                    onMovePluginDown = onMovePluginDown
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
