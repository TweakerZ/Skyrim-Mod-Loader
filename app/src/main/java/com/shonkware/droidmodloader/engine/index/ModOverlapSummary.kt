package com.shonkware.droidmodloader.engine.index

import com.shonkware.droidmodloader.engine.model.Mod

/**
 * One side of a file overlap. [fileCount] is the number of deployable paths
 * shared by the two mods, not the total number of files in either mod.
 */
data class OverlappingMod(
    val modId: String,
    val modName: String,
    val fileCount: Int
)

/**
 * Deployable-file overlap for a single mod.
 *
 * [overwrites] lists the mods whose files this mod wins over. [overwrittenBy]
 * lists the mods that win files this mod also provides. Only enabled mods are
 * considered, because disabled mods never reach the game folders.
 */
data class ModOverlapSummary(
    val overwrites: List<OverlappingMod> = emptyList(),
    val overwrittenBy: List<OverlappingMod> = emptyList()
) {
    val isEmpty: Boolean
        get() = overwrites.isEmpty() && overwrittenBy.isEmpty()

    val overwritingFileCount: Int
        get() = overwrites.sumOf { it.fileCount }

    val overwrittenFileCount: Int
        get() = overwrittenBy.sumOf { it.fileCount }
}

/**
 * Resolves which mods fight over the same deployable path and who wins.
 * Priorities follow the deployment rule: the highest priority enabled mod wins.
 */
object ModOverlapAnalyzer {
    private data class Provider(
        val modId: String,
        val modName: String,
        val priority: Int
    )

    fun analyze(
        mods: List<Mod>,
        contentIndexes: Map<String, ModContentIndex>
    ): Map<String, ModOverlapSummary> {
        val enabledMods = mods.filter { it.enabled }.sortedBy { it.priority }
        val providersByPath = linkedMapOf<String, MutableList<Provider>>()

        enabledMods.forEach { mod ->
            val index = contentIndexes[mod.id] ?: return@forEach
            index.entries
                .asSequence()
                .filter { it.isDeployable }
                .map { it.normalizedPath }
                .distinct()
                .forEach { path ->
                    providersByPath
                        .getOrPut(path) { mutableListOf() }
                        .add(Provider(mod.id, mod.name, mod.priority))
                }
        }

        val overwrites = mutableMapOf<String, MutableMap<String, OverlappingMod>>()
        val overwrittenBy = mutableMapOf<String, MutableMap<String, OverlappingMod>>()

        providersByPath.values.forEach { providers ->
            if (providers.size < 2) return@forEach

            // Stable reduce with >= keeps the later provider on equal priorities,
            // matching the resolved data graph's sort-then-take-last winner.
            val winner = providers.reduce { best, candidate ->
                if (candidate.priority >= best.priority) candidate else best
            }
            val losers = providers
                .filter { it.modId != winner.modId }
                .distinctBy { it.modId }
            if (losers.isEmpty()) return@forEach

            losers.forEach { loser ->
                accumulate(overwrites, winner.modId, loser)
                accumulate(overwrittenBy, loser.modId, winner)
            }
        }

        return mods.associate { mod ->
            mod.id to ModOverlapSummary(
                overwrites = summarize(overwrites[mod.id]),
                overwrittenBy = summarize(overwrittenBy[mod.id])
            )
        }
    }

    private fun accumulate(
        target: MutableMap<String, MutableMap<String, OverlappingMod>>,
        modId: String,
        other: Provider
    ) {
        val entries = target.getOrPut(modId) { mutableMapOf() }
        entries[other.modId] = entries[other.modId]?.let { existing ->
            existing.copy(fileCount = existing.fileCount + 1)
        } ?: OverlappingMod(
            modId = other.modId,
            modName = other.modName,
            fileCount = 1
        )
    }

    private fun summarize(
        entries: Map<String, OverlappingMod>?
    ): List<OverlappingMod> = entries
        ?.values
        ?.sortedWith(
            compareByDescending<OverlappingMod> { it.fileCount }
                .thenBy { it.modName.lowercase() }
        )
        .orEmpty()
}