package com.shonkware.droidmodloader.engine.io

import java.io.File

internal class ProfileStoragePaths(
    private val filesDir: File,
    private val activeProfileIdProvider: () -> String?,
    private val selectedGameIdProvider: () -> String
) {

    fun getActiveProfileStorageKey(): String {
        val profileId = activeProfileIdProvider()

        return if (!profileId.isNullOrBlank()) {
            sanitizeStorageName(profileId)
        } else {
            sanitizeStorageName("unassigned_${selectedGameIdProvider()}")
        }
    }

    fun getProfileInternalDir(): File {
        return File(filesDir, "profiles/${getActiveProfileStorageKey()}")
    }

    fun getProfileStateDir(externalBaseDir: File): File {
        return File(externalBaseDir, "state/profiles/${getActiveProfileStorageKey()}")
    }

    /**
     * Removes the profile's mod library, saved state, simulated deployment
     * target and deployment backups. The shared archive library is not touched.
     */
    fun deleteProfileStorage(profileId: String, externalBaseDir: File): Boolean {
        val key = sanitizeStorageName(profileId)
        val internalProfilesDir = File(filesDir, "profiles")
        val externalStateDir = File(externalBaseDir, "state/profiles")
        val deployProfilesDir = File(externalBaseDir, "deploy_target/profiles")

        return listOf(
            File(internalProfilesDir, key),
            File(externalStateDir, key),
            File(deployProfilesDir, key)
        ).all { it.deleteRecursively() }
    }

    private fun sanitizeStorageName(value: String): String {
        return value
            .replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
            .trim('_')
            .ifBlank { "default" }
    }
}
