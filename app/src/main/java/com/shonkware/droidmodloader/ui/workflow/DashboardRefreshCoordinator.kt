package com.shonkware.droidmodloader.ui.workflow

import com.shonkware.droidmodloader.engine.model.PluginEntry
import com.shonkware.droidmodloader.ui.MainActivityUiState
import java.util.concurrent.atomic.AtomicLong

internal class DashboardRefreshCoordinator(
    private val state: MainActivityUiState,
    private val buildResult: () -> DashboardRefreshResult?,
    private val runOnUiThread: (() -> Unit) -> Unit,
    private val appendLog: (String) -> Unit
) {
    private val refreshRevision = AtomicLong()

    /** True means facts were collected and publication was scheduled, not that a queued result won. */
    fun refresh(): Boolean {
        val revision = refreshRevision.incrementAndGet()
        val context = state.deploymentHealthContextKey()
        val result = buildResult() ?: return false
        runOnUiThread {
            if (revision != refreshRevision.get() || context != state.deploymentHealthContextKey()) return@runOnUiThread
            state.deploymentHealth = result.deploymentHealth
            state.deploymentHealthContext = context
            state.visibleMods = result.mods
            state.visiblePlugins = result.plugins
            state.pendingEspPlugins = mergePendingEspPlugins(state.pendingEspPlugins, result.plugins,
                state.pendingEspHasChanges)
            state.visibleModContentIndexes = result.modContentIndexes
            state.visibleModFlags = result.modFlags
            state.summaryText = result.summaryText
            appendLog("Dashboard refreshed.")
        }
        return true
    }

    /**
     * Keeps the ESP card in sync after installs, deployments, removals and other
     * dashboard refreshes. When there are no unsaved edits the pending list is
     * replaced outright; otherwise unsaved enabled/order edits are preserved for
     * plugins that still exist and newly discovered plugins are appended.
     */
    private fun mergePendingEspPlugins(
        current: List<PluginEntry>,
        incoming: List<PluginEntry>,
        hasPendingChanges: Boolean
    ): List<PluginEntry> {
        if (!hasPendingChanges || current.isEmpty()) return incoming

        val incomingByPath = incoming.associateBy { it.normalizedPath }
        val merged = current.mapNotNull { pending ->
            incomingByPath[pending.normalizedPath]?.let { fresh ->
                pending.copy(
                    pluginName = fresh.pluginName,
                    sourceModId = fresh.sourceModId,
                    sourceModName = fresh.sourceModName,
                    pluginType = fresh.pluginType,
                    sourceType = fresh.sourceType,
                    locked = fresh.locked,
                    filePresentInDataFolder = fresh.filePresentInDataFolder
                )
            }
        }.toMutableList()

        val knownPaths = current.mapTo(HashSet()) { it.normalizedPath }
        merged += incoming.filterNot { it.normalizedPath in knownPaths }
        return merged
    }
}
