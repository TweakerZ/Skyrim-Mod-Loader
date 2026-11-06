package com.shonkware.droidmodloader.engine.install

import java.io.File

/** Inputs used when evaluating FOMOD dependency conditions. */
data class InstallerConditionContext(
    val flags: Map<String, String> = emptyMap(),
    /** Extracted mod root, retained for path-based conditions. */
    val installRoot: File? = null,
    /** Lower-case plugin file names known to be installed, when available. */
    val installedPlugins: Set<String> = emptySet(),
    /** Lower-case enabled/active plugin file names; falls back to installedPlugins when empty. */
    val activePlugins: Set<String> = emptySet(),
    /** Lower-case deployed loose-asset relative paths, when available. */
    val looseFiles: Set<String> = emptySet(),
    /**
     * When true, unevaluable version / game-dependency / script-extender
     * conditions pass. Used for step visibility, module gates and conditional
     * installs (where we cannot know the user's game version). Left false for
     * typeDescriptor pattern matching so patterns fall through to the default
     * type instead of wrongly promoting an option.
     */
    val versionPass: Boolean = false
)

/**
 * A FOMOD dependency condition.
 *
 * fileDependency stays permissive when plugin state is unavailable, so
 * compatibility options are not hidden on a fresh profile. Unknown dependency
 * types are treated as unsatisfiable (fail closed) so the installer never
 * silently reveals or hides content it cannot evaluate.
 */
sealed interface InstallerCondition {

    fun isFulfilled(context: InstallerConditionContext): Boolean

    data object Always : InstallerCondition {
        override fun isFulfilled(context: InstallerConditionContext): Boolean = true
    }

    /** Explicitly unsatisfiable - used for unknown / unsupported dependencies. */
    data object Never : InstallerCondition {
        override fun isFulfilled(context: InstallerConditionContext): Boolean = false
    }

    /**
     * Version / game / script-extender / mod-manager dependency. Cannot be
     * verified from inside the archive; defers to [InstallerConditionContext.versionPass].
     */
    data object Version : InstallerCondition {
        override fun isFulfilled(context: InstallerConditionContext): Boolean = context.versionPass
    }

    data class Flag(
        val name: String,
        val value: String
    ) : InstallerCondition {
        override fun isFulfilled(context: InstallerConditionContext): Boolean {
            val actual = context.flags[name] ?: return false
            return actual.equals(value, ignoreCase = true)
        }
    }

    data class FileState(
        val path: String,
        val state: String
    ) : InstallerCondition {
        override fun isFulfilled(context: InstallerConditionContext): Boolean {
            // fileDependency refers to the game's installed plugins / deployed
            // files, not the archive. When plugin state is unavailable, stay
            // permissive so compatibility options are not hidden.
            if (context.installedPlugins.isEmpty()) return true
            val key = path.replace('\\', '/').substringAfterLast('/').lowercase()
            if (key.isEmpty()) return true
            val normKey = key.replace("\\", "/")
            val looksLoose = "/" in normKey ||
                !key.endsWith(".esp") && !key.endsWith(".esm") && !key.endsWith(".esl")

            // A loose-asset path has no enable/disable concept: present == active.
            if (looksLoose && context.looseFiles.isNotEmpty()) {
                val present = normKey in context.looseFiles
                return when (state.lowercase()) {
                    "missing" -> !present
                    "inactive" -> false
                    else -> present // "Active"
                }
            }

            val installed = context.installedPlugins.any { it.lowercase() == key }
            if (!installed) return when (state.lowercase()) {
                "missing" -> true
                else -> false // active/inactive require the file to be present
            }

            val activeSet = if (context.activePlugins.isNotEmpty()) context.activePlugins
            else context.installedPlugins
            val isActive = key in activeSet
            return when (state.lowercase()) {
                "missing" -> false
                "active" -> isActive
                "inactive" -> installed && !isActive
                else -> true
            }
        }
    }

    data class All(
        val conditions: List<InstallerCondition>
    ) : InstallerCondition {
        override fun isFulfilled(context: InstallerConditionContext): Boolean =
            conditions.all { it.isFulfilled(context) }
    }

    data class Any(
        val conditions: List<InstallerCondition>
    ) : InstallerCondition {
        override fun isFulfilled(context: InstallerConditionContext): Boolean =
            conditions.any { it.isFulfilled(context) }
    }
}
