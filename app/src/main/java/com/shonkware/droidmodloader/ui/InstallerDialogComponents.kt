package com.shonkware.droidmodloader.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shonkware.droidmodloader.engine.install.ExistingModAction
import com.shonkware.droidmodloader.engine.install.InstallerGroupType
import com.shonkware.droidmodloader.engine.install.InstallerOptionType
import com.shonkware.droidmodloader.ui.theme.DmlColors
import com.shonkware.droidmodloader.engine.install.PreparedArchiveInstall

@Composable
fun InstallerChoiceDialog(
    prepared: PreparedArchiveInstall,
    selectedOptionIds: Set<String>,
    onToggleOption: (String) -> Unit,
    onCancel: () -> Unit,
    lastOperationStatus: String = "",
    choices: InstallerDialogState = InstallerDialogState(modName = prepared.modName),
    busy: Boolean = false,
    onAction: (InstallerDialogAction) -> Unit = {}
) {
    val editor = choices.page != InstallerPage.NAME
    val files = choices.page == InstallerPage.FILES
    var warningsExpanded by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val finishNameInput = { focusManager.clearFocus(); keyboard?.hide(); Unit }
        val frame = Modifier.widthIn(max = if (editor) 720.dp else 440.dp).fillMaxWidth(0.94f)
        Card((if (editor) frame.fillMaxHeight(0.90f) else frame).testTag("installer-panel")) {
            BoxWithConstraints {
                val wideEditor = editor && maxWidth > 480.dp
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val visibleSteps = prepared.plan.visibleStepNames(selectedOptionIds)
                    val lastStepIndex = (visibleSteps.size - 1).coerceAtLeast(0)
                    val stepIndex = choices.currentStepIndex.coerceIn(0, lastStepIndex)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editor) {
                            TextButton(enabled = !busy, onClick = { finishNameInput(); onAction(InstallerDialogAction.Back) }) { Text("Back") }
                        }
                        Text("Install Mod", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (wideEditor) InstallerModName(choices.modName, busy, onAction, Modifier.weight(1f))
                    }
                    if (!wideEditor) InstallerModName(choices.modName, busy, onAction, Modifier.fillMaxWidth())
                    // Early same-name warning: shown on the name page when a mod
                    // with this name already exists. The replace decision is made
                    // at Finish via the existing-mod dialog.
                    if (!editor && choices.existingModWarning) {
                        Text(
                            "A mod named “${choices.modName}” already exists. It will be replaced when you finish.",
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (editor) {
                        Text(if (files) "Content" else "Install Options", fontWeight = FontWeight.Bold)
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth()
                                .border(1.dp, DmlColors.BorderDim, MaterialTheme.shapes.small)
                                .padding(horizontal = 4.dp)
                        ) {
                            if (files) {
                                item {
                                    Column(Modifier.padding(8.dp)) {
                                        Text("Data folder: ${choices.proposal?.rootPath?.ifEmpty { "Archive root" } ?: "Archive root"}",
                                            style = MaterialTheme.typography.bodySmall)
                                        TextButton(enabled = !busy, onClick = { finishNameInput(); onAction(InstallerDialogAction.Root("")) }) {
                                            Text("Reset to Archive Root")
                                        }
                                    }
                                }
                                items(choices.rows, key = { it.path }) { row ->
                                    ManualInstallerEntryRow(row, choices, busy, onAction)
                                }
                            } else {
                                val currentStepName = if (visibleSteps.isNotEmpty()) visibleSteps[stepIndex] else null
                                val currentStepGroups = prepared.plan
                                    .resolvedGroups(selectedOptionIds)
                                    .filter { currentStepName == null || it.stepName == currentStepName }

                                val warnings = prepared.plan.warnings
                                if (warnings.isNotEmpty()) {
                                    item {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(
                                                if (warningsExpanded) "Hide warnings (${warnings.size})" else "Show warnings (${warnings.size})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable(enabled = !busy, onClick = { warningsExpanded = !warningsExpanded })
                                            )
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                if (warningsExpanded) "▲" else "▼",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable(enabled = !busy, onClick = { warningsExpanded = !warningsExpanded })
                                            )
                                        }
                                    }
                                    if (warningsExpanded) {
                                        items(warnings) { warning ->
                                            Text(warning, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }

                                if (visibleSteps.size > 1) {
                                    item {
                                        Text(
                                            "Step ${stepIndex + 1} of ${visibleSteps.size}",
                                            Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (currentStepName?.isNotBlank() == true) {
                                    item {
                                        Text(currentStepName ?: "",
                                            Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                                currentStepGroups.forEach { group ->
                                    item { Text(group.name, Modifier.padding(8.dp), fontWeight = FontWeight.Bold) }
                                    items(group.options, key = { it.id }) { option ->
                                        val selectAll = group.type == InstallerGroupType.SELECT_ALL
                                        val checked = option.required ||
                                                (option.type.isUsable &&
                                                        (selectAll || option.id in selectedOptionIds))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = checked,
                                                enabled = !busy && !option.required && option.type.isUsable && !selectAll,
                                                onCheckedChange = { onToggleOption(option.id) })
                                            Column(Modifier.weight(1f)) {
                                                Text(option.name)
                                                if (option.description.isNotBlank()) Text(option.description,
                                                    style = MaterialTheme.typography.bodySmall)
                                                when {
                                                    option.type == InstallerOptionType.REQUIRED -> {
                                                        Text("Required",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    option.type == InstallerOptionType.NOT_USABLE -> {
                                                        Text("Not usable with the current selection",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (visibleSteps.isEmpty()) {
                                    item {
                                        Text("Choose Files uses the archive contents instead of installer options.",
                                            Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    (choices.error ?: choices.layoutError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy && lastOperationStatus.isNotBlank()) Text(lastOperationStatus)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!files) {
                            TextButton(enabled = !busy, onClick = { finishNameInput(); onAction(InstallerDialogAction.ChooseFiles) }) {
                                Text("Choose Files", maxLines = 1)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onCancel) { Text("Cancel", maxLines = 1) }
                        // Step navigation only applies on the options page; the name page
                        // always installs (advancing to options for choice-requiring plans).
                        val showStepNav = editor && !files && visibleSteps.size > 1
                        if (showStepNav && stepIndex < lastStepIndex) {
                            Button(enabled = !busy && choices.layoutError == null,
                                onClick = { finishNameInput(); onAction(InstallerDialogAction.StepNext) }) {
                                Text("Next", maxLines = 1)
                            }
                        } else {
                            Button(enabled = !busy && choices.layoutError == null,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                onClick = { finishNameInput(); onAction(InstallerDialogAction.Install) }) {
                                Text(if (showStepNav && stepIndex == lastStepIndex) "Finish" else "Install",
                                    maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
    if (choices.showUnrecognized) {
        AlertDialog(onDismissRequest = { onAction(InstallerDialogAction.DismissConfirmation) },
            text = { Text("This mod's file structure is unrecognized.") },
            confirmButton = { TextButton(onClick = { onAction(InstallerDialogAction.InstallAnyway) }) { Text("Install Anyway") } },
            dismissButton = { TextButton(onClick = { onAction(InstallerDialogAction.DismissConfirmation) }) { Text("Cancel") } })
    }
    if (choices.showExisting) {
        AlertDialog(onDismissRequest = { onAction(InstallerDialogAction.DismissConfirmation) },
            title = { Text("A mod named “${choices.modName}” already exists.") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add / Update Files keeps existing files and replaces matching files with the new ones.")
                    TextButton(onClick = { onAction(InstallerDialogAction.Existing(ExistingModAction.ADD_UPDATE)) }) {
                        Text("Add / Update Files")
                    }
                    Text("Replace removes existing files and installs only the new selection.")
                }
            },
            confirmButton = { TextButton(onClick = { onAction(InstallerDialogAction.Existing(ExistingModAction.REPLACE)) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { onAction(InstallerDialogAction.DismissConfirmation) }) { Text("Cancel") } })
    }
}

@Composable
private fun InstallerModName(name: String, busy: Boolean, onAction: (InstallerDialogAction) -> Unit, modifier: Modifier) {
    OutlinedTextField(value = name, onValueChange = { onAction(InstallerDialogAction.Name(it)) },
        label = { Text("Mod name") }, singleLine = true, enabled = !busy, modifier = modifier)
}

@Composable
private fun ManualInstallerEntryRow(
    row: ManualInstallerRow,
    choices: InstallerDialogState,
    busy: Boolean,
    onAction: (InstallerDialogAction) -> Unit
) {
    var menuOpen by remember(row.path) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val finishNameInput = { focusManager.clearFocus(); keyboard?.hide(); Unit }
    Row(Modifier.fillMaxWidth().padding(start = (row.depth.coerceAtMost(8) * 12).dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (row.directory) {
            TextButton(modifier = Modifier.size(48.dp).semantics { contentDescription = "Expand ${row.path}" },
                contentPadding = PaddingValues(0.dp), enabled = !busy,
                onClick = { finishNameInput(); onAction(InstallerDialogAction.Expand(row.path)) }) {
                Text(if (row.path in choices.expanded) "▾" else "▸")
            }
        } else Spacer(Modifier.width(48.dp))
        TriStateCheckbox(
            modifier = Modifier.semantics { contentDescription = "Include ${row.path}" },
            state = if (!row.included) ToggleableState.Off else if (row.partial) ToggleableState.Indeterminate else ToggleableState.On,
            enabled = !busy && row.enabled,
            onClick = { finishNameInput(); onAction(InstallerDialogAction.Include(row.path, !row.included || row.partial)) })
        Text(row.path.substringAfterLast('/') + if (row.directory) "/" else "",
            modifier = Modifier.weight(1f).then(if (row.directory && !busy) Modifier.clickable {
                finishNameInput()
                onAction(InstallerDialogAction.SelectFolder(row.path))
            } else Modifier),
            fontWeight = if (choices.selectedFolder == row.path) FontWeight.Bold else FontWeight.Normal)
        if (row.directory) {
            Box {
                IconButton(enabled = !busy, modifier = Modifier.semantics { contentDescription = "Folder actions ${row.path}" },
                    onClick = {
                        finishNameInput()
                        onAction(InstallerDialogAction.SelectFolder(row.path))
                        menuOpen = true
                    }) { Text("⋮", fontSize = 22.sp) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Use as Data Folder") }, onClick = {
                        menuOpen = false
                        onAction(InstallerDialogAction.Root(row.path))
                    })
                }
            }
        } else Spacer(Modifier.width(48.dp))
    }
}
