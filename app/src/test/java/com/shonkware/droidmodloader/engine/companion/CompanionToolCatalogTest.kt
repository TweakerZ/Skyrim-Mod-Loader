package com.shonkware.droidmodloader.engine.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The launch contract lives in the sibling tool repositories, so keep the
 * catalog pinned to their real activities and actions.
 */
class CompanionToolCatalogTest {
    @Test
    fun `catalog uses the short display names`() {
        assertEquals(
            listOf("Anim Tool", "Body Tool", "Loot", "ReSaver"),
            CompanionToolCatalog.tools.map { it.displayName }
        )
    }

    @Test
    fun `anim tool launches its renamed namespace activity`() {
        val tool = CompanionToolCatalog.byId("anim")!!

        assertEquals("com.skyrimandroid.animtool", tool.packageName)
        assertEquals("com.fnis.android.ui.MainActivity", tool.knownActivity)
        assertNull(tool.launchAction)
    }

    @Test
    fun `body tool keeps its legacy launch action`() {
        val tool = CompanionToolCatalog.byId("body")!!

        assertEquals("com.skyrimandroid.bodytool", tool.packageName)
        assertEquals("com.bodyslide.android.MainActivity", tool.knownActivity)
        assertEquals("com.skyrimandroid.bodytool.LAUNCH", tool.launchAction)
    }

    @Test
    fun `loot launches its renamed namespace activity`() {
        val tool = CompanionToolCatalog.byId("loot")!!

        assertEquals("com.skyrimandroid.loot", tool.packageName)
        assertEquals("com.lootandroid.MainActivity", tool.knownActivity)
        assertNull(tool.launchAction)
    }

    @Test
    fun `resaver keeps its own open action`() {
        val tool = CompanionToolCatalog.byId("resaver")!!

        assertEquals("com.skyrimandroid.resaver", tool.packageName)
        assertEquals("com.skyrimandroid.resaver.MainActivity", tool.knownActivity)
        assertEquals("com.skyrimandroid.resaver.action.OPEN", tool.launchAction)
    }
}