package com.shonkware.droidmodloader.ui.workflow

import com.shonkware.droidmodloader.engine.ModEngine
import com.shonkware.droidmodloader.engine.deploy.ScopedDeploymentResult
import com.shonkware.droidmodloader.engine.model.AppSetupState
import com.shonkware.droidmodloader.engine.model.GameProfile
import com.shonkware.droidmodloader.engine.model.Mod
import com.shonkware.droidmodloader.engine.model.PluginEntry
import com.shonkware.droidmodloader.engine.profile.ProfileRepository

internal data class FirstSetupInput(
    val profileNameText: String,
    val gameId: String,
    val targetDataPath: String,
    val realDeployEnabled: Boolean,
    val targetRootPath: String = ""
)

internal data class AdditionalProfileInput(
    val profileNameText: String,
    val gameId: String,
    val targetDataPath: String,
    val realDeployEnabled: Boolean,
    val targetRootPath: String = ""
)

internal data class DashboardProfileInput(
    val targetPathText: String,
    val rootTargetPathText: String,
    val realDeployEnabled: Boolean,
    val dataPathReselectionRequired: Boolean,
    val rootPathReselectionRequired: Boolean
)

internal interface ProfileSwitchEngine {
    fun getCurrentMods(): List<Mod>
    fun saveCurrentMods(mods: List<Mod>)
    fun getCurrentPlugins(): List<PluginEntry>
    fun saveCurrentPlugins(plugins: List<PluginEntry>)
    fun syncPlugins()
    fun deployForGame(gameId: String): ScopedDeploymentResult
}

internal class ProfileSwitchEngineAdapter(
    private val engine: ModEngine,
    private val syncPluginsAction: () -> Unit
) : ProfileSwitchEngine {
    override fun getCurrentMods(): List<Mod> = engine.getCurrentMods()

    override fun saveCurrentMods(mods: List<Mod>) {
        engine.saveCurrentMods(mods)
    }

    override fun getCurrentPlugins(): List<PluginEntry> = engine.getCurrentPlugins()

    override fun saveCurrentPlugins(plugins: List<PluginEntry>) {
        engine.saveCurrentPlugins(plugins)
    }

    override fun syncPlugins() {
        syncPluginsAction.invoke()
    }

    override fun deployForGame(gameId: String): ScopedDeploymentResult {
        return engine.deployForGame(gameId)
    }
}

internal class ProfileManagementWorkflow(
    private val repositoryProvider: () -> ProfileRepository?,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val gameDisplayNameProvider: (String) -> String,
    private val firstSetupInputProvider: () -> FirstSetupInput,
    private val additionalProfileInputProvider: () -> AdditionalProfileInput,
    private val activeProfileIdProvider: () -> String?,
    private val dashboardProfileInputProvider: () -> DashboardProfileInput,
    private val applyFirstSetupUiState: (List<GameProfile>, GameProfile) -> Unit,
    private val applyCreatedProfileUiState: (List<GameProfile>, GameProfile) -> Unit,
    private val applySwitchedProfileUiState: (GameProfile) -> Unit,
    private val applySavedProfileUiState: (List<GameProfile>, GameProfile) -> Unit,
    private val applyDeletedProfileUiStateAsync: (List<GameProfile>, GameProfile?) -> Unit,
    private val applyDeletedProfileUiStateBlocking: (List<GameProfile>, GameProfile?) -> Unit,
    private val saveSelectedGameConfigFromUi: () -> Unit,
    private val loadSelectedGameConfigIntoUi: () -> Unit,
    private val recoverActiveProfile: () -> Unit,
    private val syncPluginsFromCurrentState: () -> Unit,
    private val refreshDashboard: () -> Unit,
    private val createSwitchEngine: () -> ProfileSwitchEngine? = { null },
    private val savePendingEspOrder: () -> Boolean = { true },
    private val beginOperation: (String) -> Unit = {},
    private val finishOperation: (String) -> Unit = {},
    private val failOperation: (String, Throwable?) -> Unit = { _, _ -> },
    private val closeProfileDialog: () -> Unit = {},
    private val deleteProfileStorage: (String) -> Boolean = { true },
    private val appendLog: (String) -> Unit,
    private val appendError: (String) -> Unit,
    private val updateLastOperationStatus: (String) -> Unit
) {

    fun completeFirstSetup(input: FirstSetupInput = firstSetupInputProvider()) {
        val repo = repositoryProvider() ?: return

        if (
            !input.realDeployEnabled ||
            input.targetRootPath.isBlank() ||
            input.targetDataPath.isBlank()
        ) {
            appendError("Cannot complete setup: select a valid Game Folder first.")
            updateLastOperationStatus("Setup requires a valid Game Folder.")
            return
        }

        val profileId = "${input.gameId}_${currentTimeMillis()}"
        val cleanProfileName = input.profileNameText.trim().ifBlank { "Default" }

        val profile = GameProfile(
            profileId = profileId,
            profileName = cleanProfileName,
            gameId = input.gameId,
            gameDisplayName = gameDisplayNameProvider(input.gameId),
            targetDataPath = input.targetDataPath.trim(),
            realDeployEnabled = input.realDeployEnabled,
            targetRootPath = input.targetRootPath.trim(),
            dataPathReselectionRequired = false,
            rootPathReselectionRequired = false,
            iniPresetId = null
        )

        val existingProfiles = repo.loadProfiles().toMutableList()
        existingProfiles.add(profile)

        repo.saveProfiles(existingProfiles)
        repo.saveSetupState(
            AppSetupState(
                setupComplete = true,
                activeProfileId = profileId
            )
        )

        applyFirstSetupUiState(existingProfiles, profile)
        saveSelectedGameConfigFromUi()

        appendLog("Created first profile: $profile")
        updateLastOperationStatus("Setup complete.")
        refreshDashboard()
    }

    fun createAdditionalProfile(input: AdditionalProfileInput = additionalProfileInputProvider()) {
        val repo = repositoryProvider() ?: return

        if (
            !input.realDeployEnabled ||
            input.targetRootPath.isBlank() ||
            input.targetDataPath.isBlank()
        ) {
            appendError("Cannot create profile: select a valid Game Folder first.")
            updateLastOperationStatus("Profile creation requires a valid Game Folder.")
            return
        }

        val cleanProfileName = input.profileNameText.trim().ifBlank {
            "${gameDisplayNameProvider(input.gameId)} Profile"
        }

        val profileId = "${input.gameId}_${currentTimeMillis()}"

        val profile = GameProfile(
            profileId = profileId,
            profileName = cleanProfileName,
            gameId = input.gameId,
            gameDisplayName = gameDisplayNameProvider(input.gameId),
            targetDataPath = input.targetDataPath.trim(),
            realDeployEnabled = input.realDeployEnabled,
            targetRootPath = input.targetRootPath.trim(),
            dataPathReselectionRequired = false,
            rootPathReselectionRequired = false,
            iniPresetId = null
        )

        val profiles = repo.loadProfiles()
        val previousProfile = profiles.firstOrNull {
            it.profileId == activeProfileIdProvider()
        }
        var savedProfiles: List<GameProfile> = profiles

        activateProfile(
            repo = repo,
            target = profile,
            previousProfile = previousProfile,
            operationLabel = "Profile creation",
            operationText = "Creating profile...",
            prepareProfiles = {
                savedProfiles = repo.loadProfiles() + profile
                repo.saveProfiles(savedProfiles)
            },
            applyActivatedUi = {
                applyCreatedProfileUiState(savedProfiles, profile)
            },
            afterActivated = {
                saveSelectedGameConfigFromUi()
                recoverActiveProfile()
                syncPluginsFromCurrentState()
            },
            successLog = "Created and switched to profile: $profile",
            successStatus = "Profile created and selected: ${profile.profileName}",
            successOperationText = "Created profile: ${profile.profileName}",
            deployFailureMessage =
                "Profile ${profile.profileName} was created and selected, but the deploy failed. " +
                    "Tap Deploy to retry.",
            deployFailureStatus = "Profile created, but deploy failed: ${profile.profileName}"
        )
    }

    fun switchActiveProfile(profileId: String) {
        val repo = repositoryProvider() ?: return
        val profiles = repo.loadProfiles()
        val profile = profiles.firstOrNull { it.profileId == profileId }

        if (profile == null) {
            appendError("Could not switch profile: profile not found: $profileId")
            return
        }

        val previousProfileId = activeProfileIdProvider()
        if (previousProfileId == profile.profileId) {
            appendLog("Profile ${profile.profileName} is already active.")
            return
        }

        activateProfile(
            repo = repo,
            target = profile,
            previousProfile = profiles.firstOrNull {
                it.profileId == previousProfileId
            },
            operationLabel = "Profile switch",
            operationText = "Switching profile...",
            prepareProfiles = {},
            applyActivatedUi = {
                applySwitchedProfileUiState(profile)
            },
            afterActivated = {
                loadSelectedGameConfigIntoUi()
                recoverActiveProfile()
                syncPluginsFromCurrentState()
            },
            successLog = "Switched active profile: $profile",
            successStatus = "Switched profile: ${profile.profileName}",
            successOperationText = "Switched to profile: ${profile.profileName}",
            deployFailureMessage =
                "Profile switched to ${profile.profileName}, but the deploy failed. " +
                    "Tap Deploy to retry.",
            deployFailureStatus = "Profile switched, but deploy failed: ${profile.profileName}"
        )
    }

    private fun activateProfile(
        repo: ProfileRepository,
        target: GameProfile,
        previousProfile: GameProfile?,
        operationLabel: String,
        operationText: String,
        prepareProfiles: () -> Unit,
        applyActivatedUi: () -> Unit,
        afterActivated: () -> Unit,
        successLog: String,
        successStatus: String,
        successOperationText: String,
        deployFailureMessage: String,
        deployFailureStatus: String
    ) {
        beginOperation(operationText)
        closeProfileDialog()

        try {
            if (previousProfile != null) {
                saveActiveProfileFromDashboard()
            }

            if (!savePendingEspOrder()) {
                failOperation(
                    "$operationLabel cancelled: the pending plugin order could not be saved.",
                    null
                )
                updateLastOperationStatus("$operationLabel cancelled: ESP order not saved.")
                refreshDashboard()
                return
            }

            if (previousProfile != null) {
                val snapshot = captureProfileState(previousProfile)

                try {
                    clearDeployment(snapshot, previousProfile.gameId)
                } catch (failure: Exception) {
                    restorePreviousProfile(previousProfile, snapshot)
                    failOperation(
                        "$operationLabel cancelled: the current deployment could not be cleared. " +
                            failure.message,
                        failure
                    )
                    updateLastOperationStatus(
                        "$operationLabel cancelled: current deployment not cleared."
                    )
                    refreshDashboard()
                    return
                }

                restoreProfileState(snapshot)
            }

            prepareProfiles()
            repo.saveSetupState(
                AppSetupState(
                    setupComplete = true,
                    activeProfileId = target.profileId
                )
            )

            applyActivatedUi()
            afterActivated()

            val deployFailure = deployProfile(target)
            if (deployFailure != null) {
                failOperation(deployFailureMessage, deployFailure)
                updateLastOperationStatus(deployFailureStatus)
                refreshDashboard()
                return
            }

            refreshDashboard()
            appendLog(successLog)
            updateLastOperationStatus(successStatus)
            finishOperation(successOperationText)
        } catch (failure: Throwable) {
            failOperation("$operationLabel failed: ${failure.message}", failure)
            updateLastOperationStatus("$operationLabel failed: ${failure.message}")
            refreshDashboard()
        }
    }

    private data class ProfileSwitchState(
        val engine: ProfileSwitchEngine,
        val mods: List<Mod>,
        val plugins: List<PluginEntry>
    )

    private fun captureProfileState(profile: GameProfile): ProfileSwitchState {
        val engine = createSwitchEngine()
            ?: throw IllegalStateException(
                "Could not create an engine for profile ${profile.profileName}."
            )
        return ProfileSwitchState(
            engine = engine,
            mods = engine.getCurrentMods(),
            plugins = engine.getCurrentPlugins()
        )
    }

    /**
     * Empties the previous profile's deployment while keeping its saved mod and
     * plugin state intact. The restored state is saved back by
     * [restoreProfileState] so switching back does not lose the load order.
     */
    private fun clearDeployment(state: ProfileSwitchState, gameId: String) {
        state.engine.saveCurrentMods(state.mods.map { it.copy(enabled = false) })
        state.engine.syncPlugins()
        state.engine.deployForGame(gameId)
    }

    private fun restoreProfileState(state: ProfileSwitchState) {
        state.engine.saveCurrentMods(state.mods)
        state.engine.saveCurrentPlugins(state.plugins)
        state.engine.syncPlugins()
    }

    private fun restorePreviousProfile(profile: GameProfile, state: ProfileSwitchState) {
        try {
            restoreProfileState(state)
        } catch (failure: Exception) {
            appendError("Could not restore ${profile.profileName} mod state: ${failure.message}")
        }

        try {
            state.engine.deployForGame(profile.gameId)
            appendLog("Restored deployment for profile: ${profile.profileName}")
        } catch (failure: Exception) {
            appendError(
                "Could not restore the deployment for ${profile.profileName}: ${failure.message}"
            )
        }
    }

    private fun deployProfile(profile: GameProfile): Throwable? {
        return try {
            val engine = createSwitchEngine()
                ?: throw IllegalStateException(
                    "Could not create an engine for profile ${profile.profileName}."
                )
            engine.deployForGame(profile.gameId)
            engine.syncPlugins()
            appendLog("Deployed profile: ${profile.profileName}")
            null
        } catch (failure: Throwable) {
            failure
        }
    }

    fun saveDashboardSettings() {
        saveSelectedGameConfigFromUi()
        saveActiveProfileFromDashboard()
        refreshDashboard()
    }

    fun saveActiveProfileFromDashboard() {
        val repo = repositoryProvider() ?: return
        val currentProfileId = activeProfileIdProvider()

        if (currentProfileId == null) {
            appendError("Cannot save active profile: no active profile.")
            return
        }

        val profiles = repo.loadProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.profileId == currentProfileId }

        if (index == -1) {
            appendError("Active profile not found: $currentProfileId")
            return
        }

        val oldProfile = profiles[index]
        val dashboardInput = dashboardProfileInputProvider()
        val updatedProfile = ProfileConfigUiMapper.updatedProfileFromDashboard(
            profile = oldProfile,
            displayName = gameDisplayNameProvider(oldProfile.gameId),
            targetPathText = dashboardInput.targetPathText,
            rootTargetPathText = dashboardInput.rootTargetPathText,
            realDeployEnabled = dashboardInput.realDeployEnabled,
            dataPathReselectionRequired = dashboardInput.dataPathReselectionRequired,
            rootPathReselectionRequired = dashboardInput.rootPathReselectionRequired
        )

        profiles[index] = updatedProfile
        repo.saveProfiles(profiles)

        applySavedProfileUiState(profiles, updatedProfile)
        appendLog("Saved active profile: $updatedProfile")
    }

    fun deleteProfile(profileId: String) {
        val repo = repositoryProvider() ?: return

        val profiles = repo.loadProfiles().toMutableList()
        val profileToDelete = profiles.firstOrNull { it.profileId == profileId }

        if (profileToDelete == null) {
            appendError("Profile not found: $profileId")
            return
        }

        val currentActiveProfileId = activeProfileIdProvider()
        if (currentActiveProfileId == profileId) {
            appendError("Cannot delete the active profile. Switch to another profile first.")
            updateLastOperationStatus("Cannot delete the active profile.")
            return
        }

        beginOperation("Deleting profile...")
        closeProfileDialog()

        try {
            if (!deleteProfileStorage(profileId)) {
                appendError("Could not delete the profile files: ${profileToDelete.profileName}")
                failOperation(
                    "Profile delete failed: the profile files could not be removed.",
                    null
                )
                updateLastOperationStatus("Profile delete failed: ${profileToDelete.profileName}")
                refreshDashboard()
                return
            }

            profiles.removeAll { it.profileId == profileId }
            repo.saveProfiles(profiles)

            val activeProfileChanged = currentActiveProfileId == profileId
            val newActiveProfile = if (activeProfileChanged) {
                profiles.firstOrNull()
            } else {
                profiles.firstOrNull {
                    it.profileId == currentActiveProfileId
                }
            }

            repo.saveSetupState(
                AppSetupState(
                    setupComplete = profiles.isNotEmpty(),
                    activeProfileId =
                        newActiveProfile?.profileId
                )
            )

            applyDeletedProfileUiStateAsync(
                profiles,
                newActiveProfile
            )

            appendLog(
                "Deleted profile and its installed mods: " +
                        profileToDelete.profileName
            )
            updateLastOperationStatus(
                "Deleted profile: " +
                        profileToDelete.profileName
            )

            applyDeletedProfileUiStateBlocking(
                profiles,
                newActiveProfile
            )

            if (
                activeProfileChanged &&
                newActiveProfile != null
            ) {
                loadSelectedGameConfigIntoUi()
                recoverActiveProfile()
                syncPluginsFromCurrentState()
            }

            refreshDashboard()
            finishOperation("Deleted profile: ${profileToDelete.profileName}")
        } catch (failure: Throwable) {
            failOperation("Profile delete failed: ${failure.message}", failure)
            updateLastOperationStatus("Profile delete failed: ${failure.message}")
            refreshDashboard()
        }
    }
}
