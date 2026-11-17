package com.shonkware.droidmodloader.engine.companion

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Resolves the launch filters the suite tools actually declare. Newer builds
 * can answer the shared action, older ones only their own. Both are checked so
 * an installed tool is not reported as unlaunchable just because its action
 * predates the shared contract.
 */
internal object CompanionToolLaunchIntents {
    fun actions(tool: CompanionTool): List<String> = buildList {
        add(CompanionToolCatalog.ACTION_OPEN)
        tool.launchAction?.let { add(it) }
    }

    fun resolvedActivities(
        packageManager: PackageManager,
        tool: CompanionTool
    ): List<ResolveInfo> = actions(tool)
        .flatMap { action ->
            runCatching {
                packageManager.queryIntentActivities(
                    Intent(action)
                        .addCategory(Intent.CATEGORY_DEFAULT)
                        .setPackage(tool.packageName),
                    PackageManager.MATCH_DEFAULT_ONLY
                ).filter { it.activityInfo?.packageName == tool.packageName }
            }.getOrElse { emptyList() }
        }
        .distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
}