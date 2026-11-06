package com.shonkware.droidmodloader.engine.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamePluginRulesTest {
    private val rules = GamePluginRules()

    @Test
    fun `skyrim official plugins have the expected fixed order`() {
        val expectedRanks = linkedMapOf(
            "Skyrim.esm" to 1,
            "Update.esm" to 2,
            "Dawnguard.esm" to 3,
            "HearthFires.esm" to 4,
            "Dragonborn.esm" to 5,
            "HighResTexturePack01.esp" to 6,
            "HighResTexturePack02.esp" to 7,
            "HighResTexturePack03.esp" to 8
        )

        expectedRanks.forEach { (pluginName, expectedRank) ->
            val rule = requireNotNull(
                rules.findRule("skyrim_le", pluginName)
            )

            assertEquals(pluginName, rule.pluginName)
            assertEquals(expectedRank, rule.orderRank)

            val isBaseOrDlc = listOf("base_game", "official_dlc").contains(rule.sourceType)
            assertTrue("$pluginName should be locked", rule.locked)
            assertTrue(
                "$pluginName should be enabled by default",
                rule.defaultEnabled
            )
            assertTrue("$pluginName should be an official rule", isBaseOrDlc)
        }
    }

    @Test
    fun `skyrim dlc plugins use the official_dlc source type`() {
        listOf(
            "Dawnguard.esm",
            "HearthFires.esm",
            "Dragonborn.esm"
        ).forEach { pluginName ->
            val rule = requireNotNull(
                rules.findRule("skyrim_le", pluginName)
            )

            assertEquals("official_dlc", rule.sourceType)
            assertTrue(rule.locked)
            assertTrue(rule.defaultEnabled)
        }
    }

    @Test
    fun `high resolution texture packs are not locked`() {
        listOf(
            "HighResTexturePack01.esp",
            "HighResTexturePack02.esp",
            "HighResTexturePack03.esp"
        ).forEach { pluginName ->
            val rule = requireNotNull(
                rules.findRule("skyrim_le", pluginName)
            )

            assertFalse(
                "$pluginName should remain movable",
                rule.locked
            )
            assertTrue(
                "$pluginName should be enabled by default",
                rule.defaultEnabled
            )
        }
    }

    @Test
    fun `official plugin lookup is case insensitive`() {
        val rule = requireNotNull(
            rules.findRule("skyrim_le", "dragonborn.ESM")
        )

        assertEquals("Dragonborn.esm", rule.pluginName)
        assertEquals(5, rule.orderRank)
    }

    @Test
    fun `unknown skyrim plugins do not receive official rules`() {
        assertNull(
            rules.findRule("skyrim_le", "ExampleMod.esp")
        )
    }

    @Test
    fun `unsupported games return no rules`() {
        assertNull(rules.findRule("fallout_nv", "FalloutNV.esm"))
        assertNull(rules.findRule("oblivion", "Oblivion.esm"))
    }
}
