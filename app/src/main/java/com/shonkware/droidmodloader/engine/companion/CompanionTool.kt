package com.shonkware.droidmodloader.engine.companion

/**
 * An APK tool from the Skyrim Android suite that Skyrim Mod Loader can open.
 * The tools no longer publish a launcher icon, so this catalog is also the
 * only place that knows their install and release details.
 */
data class CompanionTool(
    val id: String,
    val displayName: String,
    val description: String,
    val packageName: String,
    val knownActivity: String,
    val releaseUrl: String,
    /**
     * First version that exposes [CompanionToolCatalog.ACTION_OPEN]. Leave null
     * until that build ships; older versions still open through the explicit
     * activity or their launcher intent.
     */
    val minimumHubVersionCode: Long? = null
)

data class InstalledCompanionTool(
    val versionName: String?,
    val versionCode: Long,
    val enabled: Boolean,
    val hubActionAvailable: Boolean,
    val explicitActivityAvailable: Boolean,
    val legacyLaunchAvailable: Boolean
)

enum class CompanionToolStatus {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_REQUIRED,
    DISABLED,
    NO_HUB_ENTRY
}

data class CompanionToolState(
    val tool: CompanionTool,
    val status: CompanionToolStatus,
    val versionName: String? = null,
    val versionCode: Long = 0L
) {
    val isInstalled: Boolean
        get() = status != CompanionToolStatus.NOT_INSTALLED
}

object CompanionToolCatalog {
    const val ACTION_OPEN = "com.tweakerz.skyrimsuite.action.OPEN"

    val tools: List<CompanionTool> = listOf(
        CompanionTool(
            id = "anim",
            displayName = "Skyrim Anim Tool",
            description = "Generates animation behaviour for FNIS and Nemesis mods.",
            packageName = "com.skyrimandroid.animtool",
            knownActivity = "com.skyrimandroid.animtool.ui.MainActivity",
            releaseUrl = "https://github.com/TweakerZ/skyrim-anim-tool/releases"
        ),
        CompanionTool(
            id = "body",
            displayName = "Skyrim Body Tool",
            description = "Builds BodySlide and Outfit Studio projects.",
            packageName = "com.skyrimandroid.bodytool",
            knownActivity = "com.skyrimandroid.bodytool.MainActivity",
            releaseUrl = "https://github.com/TweakerZ/skyrim-body-tool/releases"
        ),
        CompanionTool(
            id = "loot",
            displayName = "LOOT Android",
            description = "Sorts plugin load order with LOOT.",
            packageName = "com.skyrimandroid.loot",
            knownActivity = "com.skyrimandroid.loot.MainActivity",
            releaseUrl = "https://github.com/TweakerZ/loot-android/releases"
        ),
        CompanionTool(
            id = "resaver",
            displayName = "Skyrim ReSaver",
            description = "Cleans stale script data from save files.",
            packageName = "com.skyrimandroid.resaver",
            knownActivity = "com.skyrimandroid.resaver.MainActivity",
            releaseUrl = "https://github.com/TweakerZ/resaver-android/releases"
        )
    )

    fun byId(id: String): CompanionTool? = tools.firstOrNull { it.id == id }
}

object CompanionToolStatusResolver {
    fun resolve(
        tool: CompanionTool,
        installed: InstalledCompanionTool?
    ): CompanionToolState {
        if (installed == null) {
            return CompanionToolState(tool, CompanionToolStatus.NOT_INSTALLED)
        }

        val state = CompanionToolState(
            tool = tool,
            status = CompanionToolStatus.INSTALLED,
            versionName = installed.versionName,
            versionCode = installed.versionCode
        )

        if (!installed.enabled) {
            return state.copy(status = CompanionToolStatus.DISABLED)
        }

        val minimum = tool.minimumHubVersionCode
        if (minimum != null && installed.versionCode < minimum) {
            return state.copy(status = CompanionToolStatus.UPDATE_REQUIRED)
        }

        val launchable = installed.hubActionAvailable ||
            installed.explicitActivityAvailable ||
            installed.legacyLaunchAvailable
        return state.copy(
            status = if (launchable) {
                CompanionToolStatus.INSTALLED
            } else {
                CompanionToolStatus.NO_HUB_ENTRY
            }
        )
    }
}