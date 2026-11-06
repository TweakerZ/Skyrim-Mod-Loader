package com.shonkware.droidmodloader.engine.install

/** A single file or folder mapping within an installer option. */
data class InstallerFile(
    val source: String,
    val destination: String = "",
    /** True when [source] is a folder whose contents are copied into [destination]. */
    val isDirectory: Boolean = false,
    /** Higher priority is installed later, so it overrides lower priority mappings. */
    val priority: Int = 0,
    /** Install regardless of whether the owning option is selected. */
    val alwaysInstall: Boolean = false,
    /** Install when the owning option is usable, regardless of selection. */
    val installIfUsable: Boolean = false
)

/** Result of resolving a FOMOD option's type descriptor against condition flags. */
data class InstallerOptionTypePattern(
    val condition: InstallerCondition,
    val type: InstallerOptionType
)

data class InstallerOption(
    val id: String,
    val name: String,
    val description: String = "",
    val sourcePath: String,
    val destinationPath: String = "",
    val required: Boolean = false,
    val selectedByDefault: Boolean = false,
    val files: List<InstallerFile> = emptyList(),
    val condition: String? = null,
    /** Static option type, or the default when [typePatterns] is present. */
    val type: InstallerOptionType =
        if (required) InstallerOptionType.REQUIRED else InstallerOptionType.OPTIONAL,
    /** Default type for a conditional (`dependencyType`) descriptor. */
    val defaultType: InstallerOptionType? = null,
    /** First matching pattern wins, matching Vortex/MO2 conditional type resolution. */
    val typePatterns: List<InstallerOptionTypePattern> = emptyList(),
    /** Condition flags set when this option is selected. */
    val flags: Map<String, String> = emptyMap(),
    /** Relative path to an option image (may use backslashes), resolved against the mod root. */
    val imagePath: String = ""
) {
    /** Primary source/destination, derived from the first file mapping for backward compatibility. */
    val primaryFile: InstallerFile
        get() = files.firstOrNull() ?: InstallerFile(sourcePath, destinationPath)

    /**
     * Resolves the option's effective type against the current flag state.
     *
     * [versionPass] is false for typeDescriptor pattern matching (patterns fall
     * through to the default type for unevaluable version gates) and true only
     * for step-visibility / conditional-install contexts.
     */
    fun effectiveType(
        flagValues: Map<String, String>,
        versionPass: Boolean = false
    ): InstallerOptionType {
        val fallback = defaultType ?: type
        val context = InstallerConditionContext(flagValues, versionPass = versionPass)
        return typePatterns.firstOrNull { it.condition.isFulfilled(context) }?.type ?: fallback
    }
}
