package com.shonkware.droidmodloader.engine.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionToolStatusResolverTest {
    private val tool = CompanionTool(
        id = "loot",
        displayName = "Loot",
        description = "Sorts plugin load order with LOOT.",
        packageName = "com.skyrimandroid.loot",
        knownActivity = "com.lootandroid.MainActivity",
        releaseUrl = "https://github.com/TweakerZ/loot-android/releases"
    )

    private fun installed(
        versionCode: Long = 4,
        enabled: Boolean = true,
        hubActionAvailable: Boolean = false,
        explicitActivityAvailable: Boolean = false,
        legacyLaunchAvailable: Boolean = false
    ) = InstalledCompanionTool(
        versionName = "0.1.2",
        versionCode = versionCode,
        enabled = enabled,
        hubActionAvailable = hubActionAvailable,
        explicitActivityAvailable = explicitActivityAvailable,
        legacyLaunchAvailable = legacyLaunchAvailable
    )

    @Test
    fun `missing package is not installed`() {
        assertEquals(
            CompanionToolStatus.NOT_INSTALLED,
            CompanionToolStatusResolver.resolve(tool, null).status
        )
    }

    @Test
    fun `hub action marks the tool installed`() {
        val state = CompanionToolStatusResolver.resolve(
            tool,
            installed(hubActionAvailable = true)
        )

        assertEquals(CompanionToolStatus.INSTALLED, state.status)
        assertEquals("0.1.2", state.versionName)
        assertEquals(4L, state.versionCode)
    }

    @Test
    fun `legacy launcher is enough before the hub action ships`() {
        assertEquals(
            CompanionToolStatus.INSTALLED,
            CompanionToolStatusResolver.resolve(
                tool,
                installed(legacyLaunchAvailable = true)
            ).status
        )
    }

    @Test
    fun `installed app without any entry point reports no hub entry`() {
        assertEquals(
            CompanionToolStatus.NO_HUB_ENTRY,
            CompanionToolStatusResolver.resolve(tool, installed()).status
        )
    }

    @Test
    fun `disabled app wins over a usable entry point`() {
        assertEquals(
            CompanionToolStatus.DISABLED,
            CompanionToolStatusResolver.resolve(
                tool,
                installed(enabled = false, hubActionAvailable = true)
            ).status
        )
    }

    @Test
    fun `version below the hub minimum asks for an update`() {
        val gated = tool.copy(minimumHubVersionCode = 5)

        assertEquals(
            CompanionToolStatus.UPDATE_REQUIRED,
            CompanionToolStatusResolver.resolve(
                gated,
                installed(versionCode = 4, hubActionAvailable = true)
            ).status
        )
        assertEquals(
            CompanionToolStatus.INSTALLED,
            CompanionToolStatusResolver.resolve(
                gated,
                installed(versionCode = 5, hubActionAvailable = true)
            ).status
        )
    }
}