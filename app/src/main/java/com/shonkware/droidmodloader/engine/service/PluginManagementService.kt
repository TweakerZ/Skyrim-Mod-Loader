package com.shonkware.droidmodloader.engine.service

import com.shonkware.droidmodloader.engine.data.PluginListRepository
import com.shonkware.droidmodloader.engine.data.PluginOutputRepository
import com.shonkware.droidmodloader.engine.model.GameDeploymentConfig
import com.shonkware.droidmodloader.engine.model.Mod
import com.shonkware.droidmodloader.engine.model.PluginEntry
import com.shonkware.droidmodloader.engine.plugins.DataFolderPluginScanner
import com.shonkware.droidmodloader.engine.plugins.GamePluginLoadOrderRules
import com.shonkware.droidmodloader.engine.plugins.GamePluginRules
import com.shonkware.droidmodloader.engine.plugins.ManagedPluginScanner
import com.shonkware.droidmodloader.engine.plugins.PluginApplicationResult
import com.shonkware.droidmodloader.engine.plugins.PluginConfigurationApplier
import com.shonkware.droidmodloader.engine.plugins.PluginConfigurationApplyException
import com.shonkware.droidmodloader.engine.plugins.PluginLoadOrderMechanism
import com.shonkware.droidmodloader.engine.plugins.PluginOutputBuilder
import com.shonkware.droidmodloader.engine.plugins.PluginTimestampOrderer
import java.io.File

internal class PluginManagementService(
    pluginListFile: File,
    pluginsTxtFile: File,
    loadorderTxtFile: File,
    private val deployRootDir: File,
    private val getCurrentMods: () -> List<Mod>,
    private val getGameDeploymentConfig: (String) -> GameDeploymentConfig?,
    private val validateTargetDataPath: (String) -> Boolean
) {
    private val pluginListRepository = PluginListRepository(pluginListFile)
    private val managedPluginScanner = ManagedPluginScanner()
    private val pluginOutputRepository = PluginOutputRepository(
        pluginsTxtFile = pluginsTxtFile,
        loadorderTxtFile = loadorderTxtFile
    )
    private val gamePluginLoadOrderRules = GamePluginLoadOrderRules()
    private val pluginConfigurationApplier = PluginConfigurationApplier(
        outputBuilder = PluginOutputBuilder(),
        outputRepository = pluginOutputRepository,
        timestampOrderer = PluginTimestampOrderer()
    )
    private val dataFolderPluginScanner = DataFolderPluginScanner()
    private val gamePluginRules = GamePluginRules()


    fun discoverPluginsFromCurrentMods(): List<PluginEntry> {
        return managedPluginScanner.discoverPluginsFromEnabledMods(getCurrentMods())
    }


    fun saveDiscoveredPlugins(): List<PluginEntry> {
        val plugins = discoverPluginsFromCurrentMods()
        pluginListRepository.save(plugins)
        return plugins
    }


    fun loadPlugins(): List<PluginEntry> {
        return pluginListRepository.load()
    }


    fun getCurrentPlugins(): List<PluginEntry> {
        val saved = loadPlugins().sortedBy { it.priority }
        return if (saved.isNotEmpty()) {
            saved
        } else {
            discoverPluginsFromCurrentMods()
        }
    }


    /**
     * Returns the current plugins with their enabled state and sort order derived
     * from the Skyrim Android port's save folder ([portFolderPath]).
     *
     * - [Plugins.txt] lists the active (enabled) plugins; anything not listed is
     *   treated as inactive/disabled.
     * - [loadorder.txt] defines the display sort order; plugins missing from it
     *   fall to the end in their current order.
     *
     * Returns the unmodified current plugin list when no port folder is set.
     */
    fun getCurrentPluginsWithPortFolder(portFolderPath: String?): List<PluginEntry> {
        val plugins = getCurrentPlugins().sortedBy { it.priority }
        if (portFolderPath.isNullOrBlank()) return plugins

        val espDir = File(portFolderPath, "save/AppData/Local/Skyrim")
        val pluginsFile = File(espDir, "Plugins.txt")
        val loadorderFile = File(espDir, "loadorder.txt")

        // Tolerate both plain LE names and the "*Name.esp" SSE-style active marker.
        fun parsePluginLine(line: String): String =
            line.trim().removePrefix("*").trim().lowercase()

        val activePluginNames = if (pluginsFile.exists()) {
            pluginsFile.readText()
                .lines()
                .map(::parsePluginLine)
                .filter { it.isNotBlank() }
                .toSet()
        } else emptySet()

        val loadOrder = if (loadorderFile.exists()) {
            loadorderFile.readText()
                .lines()
                .map(::parsePluginLine)
                .filter { it.isNotBlank() }
        } else emptyList()

        val loadOrderIndex = loadOrder.withIndex().associate { (idx, name) -> name to idx }

        return plugins.map { plugin ->
            plugin.copy(enabled = activePluginNames.contains(plugin.pluginName.lowercase()))
        }.sortedWith(compareBy(
            { loadOrderIndex[it.pluginName.lowercase()] ?: Int.MAX_VALUE },
            { it.pluginName.lowercase() }
        ))
    }


    fun clearPluginList() {
        pluginListRepository.clear()
    }


    fun savePlugins(plugins: List<PluginEntry>) {
        pluginListRepository.save(plugins.sortedBy { it.priority })
    }


    fun saveCurrentPlugins(plugins: List<PluginEntry>) {
        savePlugins(plugins)
    }


    fun normalizePluginPriorities(plugins: List<PluginEntry>): List<PluginEntry> {
        return plugins.mapIndexed { index, plugin ->
            plugin.copy(priority = index + 1)
        }
    }


    fun applySavedPluginConfiguration(gameId: String): PluginApplicationResult {
        val rule = gamePluginLoadOrderRules.require(gameId)
        val plugins = loadPlugins().sortedBy { it.priority }
        val timestampDataFolder = when (rule.mechanism) {
            PluginLoadOrderMechanism.TEXT_FILES -> null
            PluginLoadOrderMechanism.FILE_TIMESTAMPS -> {
                resolvePluginTimestampDataFolder(gameId)
            }
        }

        return pluginConfigurationApplier.apply(
            rule = rule,
            plugins = plugins,
            timestampDataFolder = timestampDataFolder
        )
    }


    private fun resolvePluginTimestampDataFolder(gameId: String): File {
        val config = getGameDeploymentConfig(gameId)

        if (config?.realDeployEnabled == true) {
            if (!validateTargetDataPath(config.targetDataPath)) {
                throw PluginConfigurationApplyException(
                    "The configured local Data folder is invalid: ${config.targetDataPath}"
                )
            }

            return File(config.targetDataPath)
        }

        return deployRootDir
    }


    fun readExportedPluginsTxt(): String {
        return pluginOutputRepository.readPluginsTxt()
    }


    fun readExportedLoadorderTxt(): String {
        return pluginOutputRepository.readLoadorderTxt()
    }


    fun scanDataFolderPlugins(gameId: String): List<PluginEntry> {
        val config = getGameDeploymentConfig(gameId) ?: return emptyList()

        val foundNames = when {
            config.realDeployEnabled && validateTargetDataPath(config.targetDataPath) -> {
                dataFolderPluginScanner.scanLocalDataFolder(File(config.targetDataPath))
            }

            else -> {
                dataFolderPluginScanner.scanLocalDataFolder(deployRootDir)
            }
        }

        return foundNames.mapIndexed { index, pluginName ->
            val rule = gamePluginRules.findRule(gameId, pluginName)
            val pluginType = detectPluginType(pluginName)

            PluginEntry(
                normalizedPath = pluginName.lowercase(),
                pluginName = pluginName,
                sourceModId = rule?.sourceType ?: "unmanaged_data",
                sourceModName = when (rule?.sourceType) {
                    "base_game" -> "Base Game"
                    "official_dlc" -> "Official DLC"
                    "ttw_core" -> "TTW Core"
                    else -> "Unmanaged Data Folder"
                },
                enabled = rule?.defaultEnabled ?: false,
                priority = rule?.orderRank ?: Int.MAX_VALUE - index,
                pluginType = pluginType,
                sourceType = rule?.sourceType ?: "unmanaged_data",
                locked = rule?.locked ?: false,
                filePresentInDataFolder = true
            )
        }
    }


    private fun detectPluginType(pluginName: String): String {
        val lower = pluginName.lowercase()
        return when {
            lower.endsWith(".esm") -> "ESM"
            lower.endsWith(".esl") -> "ESL"
            else -> "ESP"
        }
    }


    fun applyPluginPriorityOrder(orderedPluginPaths: List<String>) {
        val current = getCurrentPlugins().sortedBy { it.priority }
        val byPath = current.associateBy { it.normalizedPath }

        val lockedPlugins = current
            .filter { it.locked }
            .sortedBy { it.priority }

        val lockedPaths = lockedPlugins.map { it.normalizedPath }.toSet()

        val orderedUnlockedPaths = orderedPluginPaths.filterNot { it in lockedPaths }

        val currentUnlocked = current.filterNot { it.locked }
        val currentUnlockedPaths = currentUnlocked.map { it.normalizedPath }.toSet()

        if (orderedUnlockedPaths.toSet() != currentUnlockedPaths) {
            throw IllegalArgumentException("Could not apply plugin order: ordered plugin paths do not match current unlocked plugins.")
        }

        val reorderedUnlocked = orderedUnlockedPaths.mapNotNull { byPath[it] }

        if (reorderedUnlocked.size != currentUnlocked.size) {
            throw IllegalArgumentException(
                "Could not apply plugin order: expected ${currentUnlocked.size} unlocked plugins but got ${reorderedUnlocked.size}."
            )
        }

        val reordered = lockedPlugins + reorderedUnlocked

        saveCurrentPlugins(
            reordered.mapIndexed { index, plugin ->
                plugin.copy(priority = index + 1)
            }
        )
    }
}
