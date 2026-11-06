package com.shonkware.droidmodloader.engine.install

import java.io.File
import java.util.LinkedHashMap

/** A set of files installed when its FOMOD dependency condition is fulfilled. */
data class ConditionalFileInstall(
    val condition: InstallerCondition,
    val files: List<InstallerFile>
)

data class InstallerPlan(
    val installerType: InstallerType,
    val displayName: String,
    val rootPath: String,
    val groups: List<InstallerGroup>,
    val warnings: List<String> = emptyList(),
    /** FOMOD `<requiredInstallFiles>`: always installed before selected options. */
    val requiredFiles: List<InstallerFile> = emptyList(),
    /** FOMOD `<conditionalFileInstalls>`: installed after selected options. */
    val conditionalFileInstalls: List<ConditionalFileInstall> = emptyList(),
    /** FOMOD `<moduleDependencies>` pre-install gate (evaluated at install time). */
    val moduleDependency: InstallerCondition? = null
) {
    val requiresUserChoice: Boolean
        get() = installerType == InstallerType.BAIN ||
                installerType == InstallerType.FOMOD ||
                installerType == InstallerType.MANUAL_DATA_FOLDER

    val defaultSelectedOptionIds: Set<String>
        get() = groups
            .flatMap { it.options }
            .filter { it.required || it.selectedByDefault }
            .map { it.id }
            .toSet()

    /**
     * Initial selection used by the installer dialog.
     *
     * FOMOD selection mirrors Vortex/MO2: required and recommended options are
     * preselected, SelectAll selects every usable option, SelectExactlyOne and
     * SelectAtLeastOne fall back to their first usable option when nothing is
     * required or recommended. Flag state is replayed group by group, so a
     * later group can be revealed or re-typed by an option chosen earlier in
     * the same step (the reactive behavior a live wizard shows).
     *
     * [context] supplies plugin / loose-file state for dependency evaluation.
     * When omitted, conditions stay permissive (matching the previous behavior).
     */
    fun initialSelection(context: InstallerConditionContext = InstallerConditionContext()): Set<String> {
        if (installerType != InstallerType.FOMOD) return defaultSelectedOptionIds

        val selected = mutableSetOf<String>()
        val flags = context.flags.toMutableMap()
        val root = File(rootPath)

        for (group in groups) {
            if (!group.isVisible(visibleContext(flags, context))) continue

            val options = resolveOptions(group, flags)
            val chosen = chooseDefaults(group.type, options)
            val chosenIds = chosen.map { it.id }.toSet()
            chosen.forEach { selected += it.id }

            options.forEach { option ->
                val enabled = option.type == InstallerOptionType.REQUIRED || option.id in chosenIds
                if (enabled && option.type.isUsable) {
                    flags.putAll(option.flags)
                }
            }
        }

        return selected
    }

    /**
     * Repairs a selection after visibility changes (for example, choosing an
     * option that reveals a later install step). Defaults are applied until the
     * visible set stops changing.
     */
    fun normalizeSelection(selectedOptionIds: Set<String>): Set<String> {
        if (installerType != InstallerType.FOMOD) return selectedOptionIds

        var current = selectedOptionIds
        repeat(8) {
            val groups = resolvedGroups(current)
            val next = InstallerOptionSelectionHelper
                .normalizeSelection(groups, current)
            if (next == current) return next
            current = next
        }
        return current
    }

    /**
     * Resolves group/option visibility and conditional option types against the
     * current selection, top-down just like a FOMOD wizard. Flag state is
     * replayed group by group, so a later group can be revealed or re-typed by
     * an option chosen earlier in the same step. Hidden steps never contribute
     * flags.
     *
     * [context] supplies plugin / loose-file state for dependency evaluation.
     */
    /**
     * The ordered list of visible install step names for the current selection,
     * used to drive the wizard's step pagination.
     */
    fun visibleStepNames(
        selectedOptionIds: Set<String>,
        context: InstallerConditionContext = InstallerConditionContext()
    ): List<String> {
        if (installerType != InstallerType.FOMOD) return emptyList()
        return resolvedGroups(selectedOptionIds, context)
            .map { it.stepName }
            .distinct()
    }

    fun resolvedGroups(
        selectedOptionIds: Set<String>,
        context: InstallerConditionContext = InstallerConditionContext()
    ): List<InstallerGroup> {
        if (installerType != InstallerType.FOMOD) return groups

        val result = mutableListOf<InstallerGroup>()
        val flags = context.flags.toMutableMap()
        val root = File(rootPath)

        for (group in groups) {
            if (!group.isVisible(visibleContext(flags, context))) continue

            val options = resolveOptions(group, flags)
            result += group.copy(options = options)

            options.forEach { option ->
                val enabled = option.type == InstallerOptionType.REQUIRED ||
                        option.id in selectedOptionIds
                if (enabled && option.type.isUsable) {
                    flags.putAll(option.flags)
                }
            }
        }

        return result
    }

    /**
     * Validates the current selection against each visible group's type
     * constraint, mirroring MO2 / Vortex. Returns error messages (empty = all
     * valid). Groups in which every option is NotUsable are unsatisifiable and
     * waived rather than hard-blocking the install.
     *
     * [context] supplies plugin / loose-file state for dependency evaluation.
     */
    fun validateSelections(
        selectedOptionIds: Set<String>,
        context: InstallerConditionContext = InstallerConditionContext()
    ): List<String> {
        if (installerType != InstallerType.FOMOD) return emptyList()

        val errors = mutableListOf<String>()
        val flags = context.flags.toMutableMap()
        val root = File(rootPath)

        for (group in groups) {
            if (!group.isVisible(visibleContext(flags, context))) continue

            val options = resolveOptions(group, flags)
            val selectedInGroup = options.filter { it.id in selectedOptionIds && it.type.isUsable }
            val count = selectedInGroup.size
            val satisfiable = options.any { it.type.isUsable }

            when (group.type) {
                InstallerGroupType.SELECT_EXACTLY_ONE ->
                    if (satisfiable && count != 1) {
                        errors += "\"${group.name}\": select exactly one option."
                    }
                InstallerGroupType.SELECT_AT_LEAST_ONE ->
                    if (satisfiable && count < 1) {
                        errors += "\"${group.name}\": select at least one option."
                    }
                InstallerGroupType.SELECT_AT_MOST_ONE ->
                    if (count > 1) {
                        errors += "\"${group.name}\": select at most one option."
                    }
                InstallerGroupType.SELECT_ANY, InstallerGroupType.SELECT_ALL -> {
                    // SelectAny and SelectAll have no constraint to enforce here.
                }
            }

            options.forEach { option ->
                val enabled = option.type == InstallerOptionType.REQUIRED ||
                        option.id in selectedOptionIds
                if (enabled && option.type.isUsable) {
                    flags.putAll(option.flags)
                }
            }
        }

        return errors
    }

    /**
     * Flattens the plan into copy operations in Vortex/MO2 order:
     * required files first, selected/always-install option files next, then
     * conditional files. Higher priorities are installed later.
     *
     * [context] supplies plugin / loose-file state for dependency evaluation.
     */
    fun resolveInstallFiles(
        selectedOptionIds: Set<String>,
        context: InstallerConditionContext = InstallerConditionContext()
    ): List<InstallerFile> {
        if (installerType != InstallerType.FOMOD) {
            return groups
                .flatMap { it.options }
                .filter { option ->
                    option.required || option.id in selectedOptionIds
                }
                .flatMap { option ->
                    if (option.files.isNotEmpty()) option.files
                    else listOf(InstallerFile(option.sourcePath, option.destinationPath))
                }
        }

        val resolved = resolvedGroups(selectedOptionIds, context)
        val flags = mutableMapOf<String, String>()
        resolved.forEach { group ->
            group.options.forEach { option ->
                val enabled = option.type == InstallerOptionType.REQUIRED ||
                        option.id in selectedOptionIds
                if (enabled && option.type.isUsable) {
                    flags.putAll(option.flags)
                }
            }
        }
        val installContext = InstallerConditionContext(
            flags, File(rootPath),
            context.installedPlugins, context.activePlugins, context.looseFiles,
            versionPass = true
        )

        val entries = mutableListOf<ResolvedFile>()

        fun add(files: List<InstallerFile>, offset: Int) {
            files.sortedWith(installableFileComparator).forEach { file ->
                entries += ResolvedFile(file, saturatingAdd(file.priority, offset))
            }
        }

        add(requiredFiles, REQUIRED_PRIORITY_OFFSET)

        resolved.forEach { group ->
            group.options.forEach { option ->
                val isSelected = option.type.isUsable &&
                        (option.type == InstallerOptionType.REQUIRED ||
                                option.id in selectedOptionIds)
                val files = if (isSelected) {
                    option.files
                } else {
                    option.files.filter { file ->
                        file.alwaysInstall ||
                                (file.installIfUsable && option.type.isUsable)
                    }
                }
                add(files, 0)
            }
        }

        conditionalFileInstalls.forEach { conditional ->
            if (conditional.condition.isFulfilled(installContext)) {
                add(conditional.files, CONDITIONAL_PRIORITY_OFFSET)
            }
        }

        // Coalesce file writes by destination the way the reference installer
        // does: higher priority wins, then the lexicographically greater source.
        // Folders are copied recursively and are not expanded here.
        val filesByDestination = LinkedHashMap<String, ResolvedFile>()
        val folders = mutableListOf<ResolvedFile>()
        entries.forEach { entry ->
            if (entry.file.isDirectory) {
                folders += entry
            } else {
                val key = effectiveFileDestination(entry.file)
                val existing = filesByDestination[key]
                if (existing == null || entry.winsOver(existing)) {
                    filesByDestination[key] = entry
                }
            }
        }

        return (filesByDestination.values + folders)
            .sortedBy { it.effectivePriority }
            .map { it.file }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun InstallerGroup.isVisible(
        context: InstallerConditionContext
    ): Boolean {
        val condition = visibleCondition ?: return true
        return condition.isFulfilled(context)
    }

    private fun visibleContext(
        flags: Map<String, String>,
        context: InstallerConditionContext
    ): InstallerConditionContext =
        InstallerConditionContext(
            flags, context.installRoot, context.installedPlugins, context.activePlugins,
            context.looseFiles, versionPass = true
        )

    private fun resolveOptions(
        group: InstallerGroup,
        flags: Map<String, String>
    ): List<InstallerOption> {
        return group.options.map { option ->
            val effective = option.effectiveType(flags, versionPass = false)
            option.copy(
                type = effective,
                required = effective == InstallerOptionType.REQUIRED,
                selectedByDefault = effective == InstallerOptionType.REQUIRED ||
                        effective == InstallerOptionType.RECOMMENDED
            )
        }
    }

    private fun chooseDefaults(
        groupType: InstallerGroupType,
        options: List<InstallerOption>
    ): List<InstallerOption> {
        val usable = options.filter { it.type.isUsable }
        return when (groupType) {
            InstallerGroupType.SELECT_ALL -> usable

            InstallerGroupType.SELECT_EXACTLY_ONE -> {
                val required = options.filter { it.type == InstallerOptionType.REQUIRED }
                when {
                    required.isNotEmpty() -> required
                    else -> options.firstOrNull { it.type == InstallerOptionType.RECOMMENDED }
                        ?.let { listOf(it) }
                        ?: usable.firstOrNull()?.let { listOf(it) }
                        ?: emptyList()
                }
            }

            InstallerGroupType.SELECT_AT_MOST_ONE -> {
                val required = options.filter { it.type == InstallerOptionType.REQUIRED }
                when {
                    required.isNotEmpty() -> required
                    else -> options.firstOrNull { it.type == InstallerOptionType.RECOMMENDED }
                        ?.let { listOf(it) }
                        ?: emptyList()
                }
            }

            InstallerGroupType.SELECT_AT_LEAST_ONE -> {
                val defaults = options.filter {
                    it.type == InstallerOptionType.REQUIRED ||
                            it.type == InstallerOptionType.RECOMMENDED
                }
                if (defaults.isNotEmpty()) defaults.distinctBy { it.id }
                else usable.firstOrNull()?.let { listOf(it) } ?: emptyList()
            }

            InstallerGroupType.SELECT_ANY -> options.filter {
                it.type == InstallerOptionType.REQUIRED ||
                        it.type == InstallerOptionType.RECOMMENDED
            }
        }
    }

    private fun effectiveFileDestination(file: InstallerFile): String {
        val sourceName = file.source.substringAfterLast('/')
        val destination = file.destination.replace('\\', '/').trim()
        return when {
            destination.isEmpty() -> sourceName
            destination.endsWith("/") -> destination + sourceName
            else -> destination
        }.lowercase()
    }

    private fun ResolvedFile.winsOver(other: ResolvedFile): Boolean {
        return when {
            effectivePriority != other.effectivePriority ->
                effectivePriority > other.effectivePriority
            else -> file.source.compareTo(other.file.source) > 0
        }
    }

    private data class ResolvedFile(
        val file: InstallerFile,
        val effectivePriority: Int
    )

    private companion object {
        const val CONDITIONAL_PRIORITY_OFFSET = 1_000_000_000
        const val REQUIRED_PRIORITY_OFFSET = -1_000_000_000

        fun saturatingAdd(value: Int, offset: Int): Int {
            val result = value.toLong() + offset.toLong()
            return when {
                result > Int.MAX_VALUE -> Int.MAX_VALUE
                result < Int.MIN_VALUE -> Int.MIN_VALUE
                else -> result.toInt()
            }
        }

        /**
         * Matches the reference implementation's [InstallableFile] ordering:
         * priority, files before folders, then case-insensitive source/destination.
         */
        val installableFileComparator: Comparator<InstallerFile> =
            compareBy<InstallerFile>(
                { it.priority },
                { it.isDirectory },
                { it.source.lowercase() },
                { it.destination.lowercase() }
            )
    }
}
