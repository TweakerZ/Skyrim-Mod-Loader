package com.shonkware.droidmodloader.engine.companion

import android.content.ComponentName
import android.content.pm.PackageManager

/**
 * Reads install state for the companion apps. The package names are also
 * declared in the manifest `<queries>` block; without that Android 11+ hides
 * them and every tool reports as not installed.
 */
class CompanionToolDetector(
    private val packageManager: PackageManager
) {
    fun detect(tool: CompanionTool): CompanionToolState {
        val packageInfo = runCatching {
            packageManager.getPackageInfo(tool.packageName, 0)
        }.getOrNull()

        if (packageInfo == null) {
            return CompanionToolStatusResolver.resolve(tool, null)
        }

        val hubActionAvailable = CompanionToolLaunchIntents
            .resolvedActivities(packageManager, tool)
            .isNotEmpty()

        val explicitActivityAvailable = runCatching {
            packageManager.getActivityInfo(
                ComponentName(tool.packageName, tool.knownActivity),
                0
            )
        }.isSuccess

        val legacyLaunchAvailable = runCatching {
            packageManager.getLaunchIntentForPackage(tool.packageName) != null
        }.getOrDefault(false)

        return CompanionToolStatusResolver.resolve(
            tool = tool,
            installed = InstalledCompanionTool(
                versionName = packageInfo.versionName,
                versionCode = packageInfo.longVersionCode,
                enabled = packageInfo.applicationInfo?.enabled == true,
                hubActionAvailable = hubActionAvailable,
                explicitActivityAvailable = explicitActivityAvailable,
                legacyLaunchAvailable = legacyLaunchAvailable
            )
        )
    }

    fun detectAll(): List<CompanionToolState> =
        CompanionToolCatalog.tools.map(::detect)
}