package com.shonkware.droidmodloader.engine.index

import com.shonkware.droidmodloader.engine.model.DeployScope
import com.shonkware.droidmodloader.engine.model.Mod
import com.shonkware.droidmodloader.engine.model.ModType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModOverlapAnalyzerTest {
    @Test
    fun `higher priority mod overwrites lower priority mod`() {
        val low = mod("low", priority = 1)
        val high = mod("high", priority = 2)

        val result = ModOverlapAnalyzer.analyze(
            mods = listOf(low, high),
            contentIndexes = mapOf(
                low.id to index(low, "textures/armor.dds"),
                high.id to index(high, "textures/armor.dds")
            )
        )

        val lowOverlap = result.getValue(low.id)
        assertEquals(listOf(high.id to 1), lowOverlap.overwrittenBy.map { it.modId to it.fileCount })
        assertTrue(lowOverlap.overwrites.isEmpty())

        val highOverlap = result.getValue(high.id)
        assertEquals(listOf(low.id to 1), highOverlap.overwrites.map { it.modId to it.fileCount })
        assertTrue(highOverlap.overwrittenBy.isEmpty())
    }

    @Test
    fun `shared folder paths accumulate per mod pair`() {
        val low = mod("low", priority = 1)
        val high = mod("high", priority = 3)

        val result = ModOverlapAnalyzer.analyze(
            mods = listOf(low, high),
            contentIndexes = mapOf(
                low.id to index(low, "meshes/a.nif", "meshes/b.nif"),
                high.id to index(high, "meshes/a.nif", "meshes/b.nif", "meshes/c.nif")
            )
        )

        assertEquals(
            listOf(high.id to 2),
            result.getValue(low.id).overwrittenBy.map { it.modId to it.fileCount }
        )
        assertEquals(
            listOf(low.id to 2),
            result.getValue(high.id).overwrites.map { it.modId to it.fileCount }
        )
    }

    @Test
    fun `disabled mods do not participate`() {
        val disabled = mod("disabled", priority = 9, enabled = false)
        val enabled = mod("enabled", priority = 1)

        val result = ModOverlapAnalyzer.analyze(
            mods = listOf(disabled, enabled),
            contentIndexes = mapOf(
                disabled.id to index(disabled, "meshes/a.nif"),
                enabled.id to index(enabled, "meshes/a.nif")
            )
        )

        assertTrue(result.getValue(enabled.id).isEmpty)
        assertTrue(result.getValue(disabled.id).isEmpty)
    }

    @Test
    fun `non deployable files are ignored`() {
        val low = mod("low", priority = 1)
        val high = mod("high", priority = 2)

        val result = ModOverlapAnalyzer.analyze(
            mods = listOf(low, high),
            contentIndexes = mapOf(
                low.id to index(low, "docs/readme.txt", deployable = false),
                high.id to index(high, "docs/readme.txt", deployable = false)
            )
        )

        assertTrue(result.getValue(low.id).isEmpty)
        assertTrue(result.getValue(high.id).isEmpty)
    }

    @Test
    fun `third mod loses to the highest priority provider`() {
        val low = mod("low", priority = 1)
        val middle = mod("middle", priority = 2)
        val high = mod("high", priority = 3)

        val result = ModOverlapAnalyzer.analyze(
            mods = listOf(low, middle, high),
            contentIndexes = mapOf(
                low.id to index(low, "scripts/foo.pex"),
                middle.id to index(middle, "scripts/foo.pex"),
                high.id to index(high, "scripts/foo.pex")
            )
        )

        val highWins = result.getValue(high.id).overwrites.associate { it.modId to it.fileCount }
        assertEquals(mapOf(low.id to 1, middle.id to 1), highWins)

        val lowLoses = result.getValue(low.id).overwrittenBy
        assertEquals(listOf(high.id), lowLoses.map { it.modId })
        assertEquals(listOf(high.id), result.getValue(middle.id).overwrittenBy.map { it.modId })
    }

    private fun mod(id: String, priority: Int, enabled: Boolean = true) = Mod(
        id = id,
        name = id,
        installPath = "/mods/$id",
        enabled = enabled,
        priority = priority,
        modType = ModType.LOOSE
    )

    private fun index(
        mod: Mod,
        vararg normalizedPaths: String,
        deployable: Boolean = true
    ) = ModContentIndex(
        modId = mod.id,
        modName = mod.name,
        entries = normalizedPaths.map { path ->
            ModContentEntry(
                originalPath = path,
                normalizedPath = path,
                category = ModContentCategory.GAME_FILE,
                reason = "test",
                isDeployable = deployable,
                deployScope = DeployScope.DATA
            )
        }
    )
}