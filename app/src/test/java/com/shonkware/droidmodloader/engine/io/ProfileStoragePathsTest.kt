package com.shonkware.droidmodloader.engine.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileStoragePathsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `active profile id is sanitized for storage`() {
        val paths = ProfileStoragePaths(
            filesDir = File("/internal"),
            activeProfileIdProvider = { "My Profile: TTW" },
            selectedGameIdProvider = { "fallout_nv" }
        )

        assertEquals("My_Profile_TTW", paths.getActiveProfileStorageKey())
    }

    @Test
    fun `missing active profile falls back to selected game`() {
        val paths = ProfileStoragePaths(
            filesDir = File("/internal"),
            activeProfileIdProvider = { null },
            selectedGameIdProvider = { "skyrim_le" }
        )

        assertEquals("unassigned_skyrim_le", paths.getActiveProfileStorageKey())
    }

    @Test
    fun `profile directories use the active storage key`() {
        val paths = ProfileStoragePaths(
            filesDir = File("/internal"),
            activeProfileIdProvider = { "Default Profile" },
            selectedGameIdProvider = { "skyrim_le" }
        )

        assertEquals(
            File("/internal/profiles/Default_Profile"),
            paths.getProfileInternalDir()
        )
        assertEquals(
            File("/external/state/profiles/Default_Profile"),
            paths.getProfileStateDir(File("/external"))
        )
    }

    @Test
    fun `delete profile storage removes mods state deploy target and backups`() {
        val internal = temporaryFolder.newFolder("internal")
        val external = temporaryFolder.newFolder("external")
        val paths = ProfileStoragePaths(
            filesDir = internal,
            activeProfileIdProvider = { "Default" },
            selectedGameIdProvider = { "skyrim_le" }
        )
        val profileId = "My Profile: TTW"
        val key = "My_Profile_TTW"

        val modsDir = File(internal, "profiles/$key/mods/bigmod")
        modsDir.mkdirs()
        File(modsDir, "file.esp").writeText("x")

        val stateDir = File(external, "state/profiles/$key")
        stateDir.mkdirs()
        File(stateDir, "installed_mods.json").writeText("[]")

        val deployDir = File(external, "deploy_target/profiles/$key/skyrim_le/Data")
        deployDir.mkdirs()
        File(deployDir, "mod.esp").writeText("x")
        File(
            external,
            "deploy_target/profiles/$key/skyrim_le/_dml_deployment_backups/Data"
        ).mkdirs()

        val otherProfileDir = File(internal, "profiles/other")
        otherProfileDir.mkdirs()

        assertTrue(paths.deleteProfileStorage(profileId, external))

        assertFalse(File(internal, "profiles/$key").exists())
        assertFalse(stateDir.exists())
        assertFalse(File(external, "deploy_target/profiles/$key").exists())
        assertTrue(otherProfileDir.exists())
    }
}
