package com.shonkware.droidmodloader.engine.companion

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens a companion app through the shared suite action or the tool's own
 * action. Older builds without either are reached by their explicit activity,
 * and builds from before the drawer cleanup fall back to their launcher intent.
 */
class CompanionToolLauncher(
    private val context: Context
) {
    fun launch(tool: CompanionTool): Boolean {
        val packageManager = context.packageManager
        val actionActivities = CompanionToolLaunchIntents
            .resolvedActivities(packageManager, tool)

        val candidates = buildList {
            actionActivities.forEach { resolved ->
                add(
                    Intent().setComponent(
                        ComponentName(
                            resolved.activityInfo.packageName,
                            resolved.activityInfo.name
                        )
                    )
                )
            }
            add(Intent().setComponent(ComponentName(tool.packageName, tool.knownActivity)))
            packageManager.getLaunchIntentForPackage(tool.packageName)?.let(::add)
        }

        return candidates.any(::start)
    }

    fun openReleasePage(tool: CompanionTool): Boolean =
        start(Intent(Intent.ACTION_VIEW, Uri.parse(tool.releaseUrl)))

    fun openAppDetails(tool: CompanionTool): Boolean =
        start(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${tool.packageName}")
            )
        )

    private fun start(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}