package com.shonkware.droidmodloader.ui.workflow

import com.shonkware.droidmodloader.engine.deploy.DeploymentResult
import com.shonkware.droidmodloader.engine.deploy.ScopedDeploymentResult
import com.shonkware.droidmodloader.engine.model.AppSetupState
import com.shonkware.droidmodloader.engine.model.GameProfile
import com.shonkware.droidmodloader.engine.model.Mod
import com.shonkware.droidmodloader.engine.model.ModType
import com.shonkware.droidmodloader.engine.model.PluginEntry
import com.shonkware.droidmodloader.engine.profile.ProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileManagementWorkflowTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun completeFirstSetupCreatesDefaultProfileAndActivatesIt() {
        val repository = createRepository("first-setup")
        var appliedProfile: GameProfile? = null
        var savedConfigCount = 0
        var refreshCount = 0
        val statuses = mutableListOf<String>()

        val workflow = createWorkflow(
            repository = repository,
            currentTimeMillis = { 1234L },
            firstSetupInputProvider = {
                FirstSetupInput(
                    profileNameText = "   ",
                    gameId = "skyrim_le",
                    targetDataPath = "/games/skyrim/Data",
                    realDeployEnabled = true,
                    targetRootPath = "/games/skyrim"
                )
            },
            applyFirstSetupUiState = { _, profile ->
                appliedProfile = profile
            },
            saveSelectedGameConfigFromUi = {
                savedConfigCount++
            },
            refreshDashboard = {
                refreshCount++
            },
            updateLastOperationStatus = {
                statuses.add(it)
            }
        )

        workflow.completeFirstSetup()

        val profiles = repository.loadProfiles()
        assertEquals(1, profiles.size)
        assertEquals("skyrim_le_1234", profiles.single().profileId)
        assertEquals("Default", profiles.single().profileName)
        assertEquals("/games/skyrim/Data", profiles.single().targetDataPath)
        assertEquals("/games/skyrim", profiles.single().targetRootPath)
        assertTrue(profiles.single().realDeployEnabled)
        assertEquals(profiles.single(), appliedProfile)
        assertEquals(
            AppSetupState(
                setupComplete = true,
                activeProfileId = "skyrim_le_1234"
            ),
            repository.loadSetupState()
        )
        assertEquals(1, savedConfigCount)
        assertEquals(1, refreshCount)
        assertEquals(listOf("Setup complete."), statuses)
    }

    @Test
    fun createAdditionalProfileUsesFallbackNameAndResolvedInstallation() {
        val repository = createRepository("additional-profile")
        var appliedProfile: GameProfile? = null
        val events = mutableListOf<String>()
        val targetEngine = FakeSwitchEngine(events = events, label = "target")
        val engines = ArrayDeque<ProfileSwitchEngine>()
        engines.add(targetEngine)

        val workflow = createWorkflow(
            repository = repository,
            currentTimeMillis = { 2000L },
            additionalProfileInputProvider = {
                AdditionalProfileInput(
                    profileNameText = "",
                    gameId = "fallout_nv",
                    targetDataPath = "/games/falloutnv/Data",
                    realDeployEnabled = true,
                    targetRootPath = "/games/falloutnv"
                )
            },
            applyCreatedProfileUiState = { _, profile ->
                appliedProfile = profile
                events += "apply"
            },
            saveSelectedGameConfigFromUi = {
                events += "save-config"
            },
            recoverActiveProfile = {
                events += "recover"
            },
            syncPluginsFromCurrentState = {
                events += "sync"
            },
            refreshDashboard = {
                events += "refresh"
            },
            createSwitchEngine = { engines.removeFirstOrNull() },
            savePendingEspOrder = {
                events += "save-esp"
                true
            },
            beginOperation = { events += "begin" },
            finishOperation = { events += "finish" },
            closeProfileDialog = { events += "close" }
        )

        workflow.createAdditionalProfile()

        val profile = repository.loadProfiles().single()
        assertEquals("fallout_nv_2000", profile.profileId)
        assertEquals("Fallout New Vegas Profile", profile.profileName)
        assertEquals("/games/falloutnv/Data", profile.targetDataPath)
        assertEquals("/games/falloutnv", profile.targetRootPath)
        assertTrue(profile.realDeployEnabled)
        assertEquals(profile, appliedProfile)
        assertEquals(
            "fallout_nv_2000",
            repository.loadSetupState().activeProfileId
        )
        assertEquals(1, targetEngine.deployCount)
        assertEquals(
            listOf(
                "begin",
                "close",
                "save-esp",
                "apply",
                "save-config",
                "recover",
                "sync",
                "target:deploy-fallout_nv",
                "target:sync",
                "refresh",
                "finish"
            ),
            events
        )
    }

    @Test
    fun createAdditionalProfileClearsPreviousDeploymentBeforeActivatingNewProfile() {
        val repository = createRepository("additional-profile-switch")
        val currentProfile = profile("current", "Current", "skyrim_le")
        repository.saveProfiles(listOf(currentProfile))
        repository.saveSetupState(
            AppSetupState(
                setupComplete = true,
                activeProfileId = currentProfile.profileId
            )
        )

        val events = mutableListOf<String>()
        val originalMods = listOf(mod("a"), mod("b", enabled = false))
        val originalPlugins = listOf(plugin("A.esp"))
        val previousEngine = FakeSwitchEngine(
            initialMods = originalMods,
            initialPlugins = originalPlugins,
            events = events,
            label = "previous"
        )
        val targetEngine = FakeSwitchEngine(events = events, label = "target")
        val engines = ArrayDeque<ProfileSwitchEngine>()
        engines.add(previousEngine)
        engines.add(targetEngine)

        var appliedProfiles: List<GameProfile> = emptyList()
        var appliedProfile: GameProfile? = null
        val workflow = createWorkflow(
            repository = repository,
            currentTimeMillis = { 2000L },
            activeProfileIdProvider = { currentProfile.profileId },
            additionalProfileInputProvider = {
                AdditionalProfileInput(
                    profileNameText = "Second",
                    gameId = "fallout_nv",
                    targetDataPath = "/games/falloutnv/Data",
                    realDeployEnabled = true,
                    targetRootPath = "/games/falloutnv"
                )
            },
            applyCreatedProfileUiState = { profiles, profile ->
                appliedProfiles = profiles
                appliedProfile = profile
                events += "apply"
            },
            refreshDashboard = {
                events += "refresh"
            },
            createSwitchEngine = { engines.removeFirstOrNull() },
            savePendingEspOrder = {
                events += "save-esp"
                true
            },
            beginOperation = { events += "begin" },
            finishOperation = { events += "finish" },
            closeProfileDialog = { events += "close" }
        )

        workflow.createAdditionalProfile()

        val savedProfiles = repository.loadProfiles()
        val newProfile = savedProfiles.first { it.profileId != currentProfile.profileId }
        assertEquals(2, savedProfiles.size)
        assertEquals("Second", newProfile.profileName)
        assertEquals(newProfile.profileId, repository.loadSetupState().activeProfileId)
        assertEquals(savedProfiles, appliedProfiles)
        assertEquals(newProfile, appliedProfile)
        assertEquals(originalMods, previousEngine.mods)
        assertEquals(originalPlugins, previousEngine.plugins)
        assertEquals(1, previousEngine.deployCount)
        assertEquals(1, targetEngine.deployCount)
        assertEquals(
            listOf(
                "begin",
                "close",
                "save-esp",
                "previous:get-mods",
                "previous:get-plugins",
                "previous:disable-mods",
                "previous:sync",
                "previous:deploy-skyrim_le",
                "previous:restore-mods",
                "previous:save-plugins",
                "previous:sync",
                "apply",
                "target:deploy-fallout_nv",
                "target:sync",
                "refresh",
                "finish"
            ),
            events
        )
    }

    @Test
    fun switchActiveProfileSavesCurrentDashboardStateBeforeActivatingTarget() {
        val repository = createRepository("switch-profile")
        val currentProfile = profile(
            id = "current",
            name = "Current",
            gameId = "skyrim_le"
        )
        val targetProfile = profile(
            id = "target",
            name = "Target",
            gameId = "fallout_nv"
        )
        repository.saveProfiles(listOf(currentProfile, targetProfile))
        repository.saveSetupState(
            AppSetupState(
                setupComplete = true,
                activeProfileId = currentProfile.profileId
            )
        )

        var activeProfileId: String? = currentProfile.profileId
        var switchedProfile: GameProfile? = null
        val events = mutableListOf<String>()
        val originalMods = listOf(mod("a"), mod("b", enabled = false))
        val originalPlugins = listOf(plugin("A.esp"))
        val previousEngine = FakeSwitchEngine(
            initialMods = originalMods,
            initialPlugins = originalPlugins,
            events = events,
            label = "previous"
        )
        val targetEngine = FakeSwitchEngine(events = events, label = "target")
        val engines = ArrayDeque<ProfileSwitchEngine>()
        engines.add(previousEngine)
        engines.add(targetEngine)

        val workflow = createWorkflow(
            repository = repository,
            activeProfileIdProvider = { activeProfileId },
            dashboardProfileInputProvider = {
                DashboardProfileInput(
                    targetPathText = " /games/skyrim/Data ",
                    rootTargetPathText = " /games/skyrim ",
                    realDeployEnabled = true,
                    dataPathReselectionRequired = false,
                    rootPathReselectionRequired = false
                )
            },
            applySwitchedProfileUiState = { profile ->
                switchedProfile = profile
                activeProfileId = profile.profileId
                events += "apply"
            },
            loadSelectedGameConfigIntoUi = {
                events += "load-config"
            },
            recoverActiveProfile = {
                events += "recover"
            },
            syncPluginsFromCurrentState = {
                events += "sync"
            },
            refreshDashboard = {
                events += "refresh"
            },
            createSwitchEngine = { engines.removeFirstOrNull() },
            savePendingEspOrder = {
                events += "save-esp"
                true
            },
            beginOperation = { events += "begin" },
            finishOperation = { events += "finish" },
            failOperation = { message, _ -> events += "fail:$message" },
            closeProfileDialog = { events += "close" }
        )

        workflow.switchActiveProfile(targetProfile.profileId)

        val savedProfiles = repository.loadProfiles()
        val savedCurrent = savedProfiles.first {
            it.profileId == currentProfile.profileId
        }
        assertEquals("/games/skyrim/Data", savedCurrent.targetDataPath)
        assertEquals("/games/skyrim", savedCurrent.targetRootPath)
        assertTrue(savedCurrent.realDeployEnabled)
        assertEquals(targetProfile, switchedProfile)
        assertEquals(
            targetProfile.profileId,
            repository.loadSetupState().activeProfileId
        )
        assertEquals(originalMods, previousEngine.mods)
        assertEquals(originalPlugins, previousEngine.plugins)
        assertEquals(1, previousEngine.deployCount)
        assertEquals(1, targetEngine.deployCount)
        assertEquals(
            listOf(
                "begin",
                "close",
                "save-esp",
                "previous:get-mods",
                "previous:get-plugins",
                "previous:disable-mods",
                "previous:sync",
                "previous:deploy-skyrim_le",
                "previous:restore-mods",
                "previous:save-plugins",
                "previous:sync",
                "apply",
                "load-config",
                "recover",
                "sync",
                "target:deploy-fallout_nv",
                "target:sync",
                "refresh",
                "finish"
            ),
            events
        )
    }

    @Test
    fun switchActiveProfileAbortsWhenPendingEspOrderCannotBeSaved() {
        val repository = createRepository("switch-esp-save-failure")
        val currentProfile = profile("current", "Current", "skyrim_le")
        val targetProfile = profile("target", "Target", "fallout_nv")
        repository.saveProfiles(listOf(currentProfile, targetProfile))
        repository.saveSetupState(
            AppSetupState(
                setupComplete = true,
                activeProfileId = currentProfile.profileId
            )
        )

        val events = mutableListOf<String>()
        val previousEngine = FakeSwitchEngine(events = events, label = "previous")

        val workflow = createWorkflow(
            repository = repository,
            activeProfileIdProvider = { currentProfile.profileId },
            createSwitchEngine = { previousEngine },
            savePendingEspOrder = { false },
            beginOperation = { events += "begin" },
            failOperation = { message, _ -> events += "fail:$message" }
        )

        workflow.switchActiveProfile(targetProfile.profileId)

        assertEquals(
            currentProfile.profileId,
            repository.loadSetupState().activeProfileId
        )
        assertEquals(0, previousEngine.deployCount)
        assertTrue(events.any { it.startsWith("fail:Profile switch cancelled") })
    }

    @Test
    fun switchActiveProfileRestoresPreviousProfileWhenDeploymentCannotBeCleared() {
        val repository = createRepository("switch-clear-failure")
        val currentProfile = profile("current", "Current", "skyrim_le")
        val targetProfile = profile("target", "Target", "fallout_nv")
        repository.saveProfiles(listOf(currentProfile, targetProfile))
        repository.saveSetupState(
            AppSetupState(
                setupComplete = true,
                activeProfileId = currentProfile.profileId
            )
        )

        val events = mutableListOf<String>()
        val originalMods = listOf(mod("a"), mod("b", enabled = false))
        val originalPlugins = listOf(plugin("A.esp"))
        val previousEngine = FakeSwitchEngine(
            initialMods = originalMods,
            initialPlugins = originalPlugins,
            events = events,
            label = "previous",
            failDeployments = 1
        )
        val engines = ArrayDeque<ProfileSwitchEngine>()
        engines.add(previousEngine)

        var switchedProfile: GameProfile? = null
        val workflow = createWorkflow(
            repository = repository,
            activeProfileIdProvider = { currentProfile.profileId },
            applySwitchedProfileUiState = { switchedProfile = it },
            createSwitchEngine = { engines.removeFirstOrNull() },
            beginOperation = { events += "begin" },
            failOperation = { message, _ -> events += "fail:$message" }
        )

        workflow.switchActiveProfile(targetProfile.profileId)

        assertEquals(
            currentProfile.profileId,
            repository.loadSetupState().activeProfileId
        )
        assertEquals(null, switchedProfile)
        assertEquals(2, previousEngine.deployCount)
        assertEquals(originalMods, previousEngine.mods)
        assertEquals(originalPlugins, previousEngine.plugins)
        assertTrue(events.any { it.startsWith("fail:Profile switch cancelled") })
    }

    @Test
    fun switchActiveProfileKeepsNewProfileWhenItsDeploymentFails() {
        val repository = createRepository("switch-target-deploy-failure")
        val currentProfile = profile("current", "Current", "skyrim_le")
        val targetProfile = profile("target", "Target", "fallout_nv")
        repository.saveProfiles(listOf(currentProfile, targetProfile))
        repository.saveSetupState(
            AppSetupState(
                setupComplete = true,
                activeProfileId = currentProfile.profileId
            )
        )

        val events = mutableListOf<String>()
        val previousEngine = FakeSwitchEngine(events = events, label = "previous")
        val targetEngine = FakeSwitchEngine(
            events = events,
            label = "target",
            failDeployments = 1
        )
        val engines = ArrayDeque<ProfileSwitchEngine>()
        engines.add(previousEngine)
        engines.add(targetEngine)

        var switchedProfile: GameProfile? = null
        var failureMessage: String? = null
        var finishCount = 0
        val workflow = createWorkflow(
            repository = repository,
            activeProfileIdProvider = { currentProfile.profileId },
            applySwitchedProfileUiState = { switchedProfile = it },
            createSwitchEngine = { engines.removeFirstOrNull() },
            beginOperation = { events += "begin" },
            finishOperation = { finishCount++ },
            failOperation = { message, _ -> failureMessage = message }
        )

        workflow.switchActiveProfile(targetProfile.profileId)

        assertEquals(
            targetProfile.profileId,
            repository.loadSetupState().activeProfileId
        )
        assertEquals(targetProfile, switchedProfile)
        assertEquals(0, finishCount)
        assertEquals(1, targetEngine.deployCount)
        assertTrue(failureMessage?.contains("Profile switched") == true)
        assertTrue(failureMessage?.contains("Tap Deploy") == true)
    }

    @Test
    fun deletingActiveProfileIsRefused() {
        val repository = createRepository("delete-active-profile")
        val firstProfile = profile("first", "First", "skyrim_le")
        val secondProfile = profile("second", "Second", "fallout_nv")
        repository.saveProfiles(listOf(firstProfile, secondProfile))
        repository.saveSetupState(
            AppSetupState(
                setupComplete = true,
                activeProfileId = firstProfile.profileId
            )
        )

        var storageDeleted = false
        var uiApplied = false
        val errors = mutableListOf<String>()
        val statuses = mutableListOf<String>()

        val workflow = createWorkflow(
            repository = repository,
            activeProfileIdProvider = { firstProfile.profileId },
            deleteProfileStorage = {
                storageDeleted = true
                true
            },
            applyDeletedProfileUiStateAsync = { _, _ -> uiApplied = true },
            applyDeletedProfileUiStateBlocking = { _, _ -> uiApplied = true },
            appendError = errors::add,
            updateLastOperationStatus = statuses::add
        )

        workflow.deleteProfile(firstProfile.profileId)

        assertEquals(
            listOf(firstProfile, secondProfile),
            repository.loadProfiles()
        )
        assertEquals(
            firstProfile.profileId,
            repository.loadSetupState().activeProfileId
        )
        assertEquals(false, storageDeleted)
        assertEquals(false, uiApplied)
        assertTrue(errors.any { it.contains("Cannot delete the active profile") })
        assertTrue(statuses.any { it.contains("Cannot delete the active profile") })
    }

    @Test
    fun deletingInactiveProfileRemovesItsStorageAndDoesNotRecoverActiveProfile() {
        val repository = createRepository("delete-inactive-profile")
        val activeProfile = profile("active", "Active", "skyrim_le")
        val inactiveProfile = profile("inactive", "Inactive", "fallout_nv")
        repository.saveProfiles(listOf(activeProfile, inactiveProfile))
        repository.saveSetupState(
            AppSetupState(
                setupComplete = true,
                activeProfileId = activeProfile.profileId
            )
        )

        var recoveryCount = 0
        var syncCount = 0
        var refreshCount = 0
        val deletedIds = mutableListOf<String>()
        val events = mutableListOf<String>()

        val workflow = createWorkflow(
            repository = repository,
            activeProfileIdProvider = { activeProfile.profileId },
            recoverActiveProfile = {
                recoveryCount++
            },
            syncPluginsFromCurrentState = {
                syncCount++
            },
            refreshDashboard = {
                refreshCount++
            },
            applyDeletedProfileUiStateAsync = { _, _ ->
                events += "apply-async"
            },
            applyDeletedProfileUiStateBlocking = { _, _ ->
                events += "apply-blocking"
            },
            deleteProfileStorage = { profileId ->
                deletedIds += profileId
                events += "delete-storage"
                true
            },
            beginOperation = { events += "begin" },
            finishOperation = { events += "finish" },
            closeProfileDialog = { events += "close" }
        )

        workflow.deleteProfile(inactiveProfile.profileId)

        assertEquals(listOf(activeProfile), repository.loadProfiles())
        assertEquals(
            activeProfile.profileId,
            repository.loadSetupState().activeProfileId
        )
        assertEquals(listOf(inactiveProfile.profileId), deletedIds)
        assertEquals(0, recoveryCount)
        assertEquals(0, syncCount)
        assertEquals(1, refreshCount)
        assertEquals(
            listOf(
                "begin",
                "close",
                "delete-storage",
                "apply-async",
                "apply-blocking",
                "finish"
            ),
            events
        )
    }

    @Test
    fun deletingInactiveProfileStopsWhenItsStorageCannotBeRemoved() {
        val repository = createRepository("delete-inactive-storage-failure")
        val activeProfile = profile("active", "Active", "skyrim_le")
        val inactiveProfile = profile("inactive", "Inactive", "fallout_nv")
        repository.saveProfiles(listOf(activeProfile, inactiveProfile))
        repository.saveSetupState(
            AppSetupState(
                setupComplete = true,
                activeProfileId = activeProfile.profileId
            )
        )

        var uiApplied = false
        var failed = false
        val workflow = createWorkflow(
            repository = repository,
            activeProfileIdProvider = { activeProfile.profileId },
            applyDeletedProfileUiStateAsync = { _, _ -> uiApplied = true },
            applyDeletedProfileUiStateBlocking = { _, _ -> uiApplied = true },
            deleteProfileStorage = { false },
            failOperation = { _, _ -> failed = true }
        )

        workflow.deleteProfile(inactiveProfile.profileId)

        assertEquals(
            listOf(activeProfile, inactiveProfile),
            repository.loadProfiles()
        )
        assertEquals(
            activeProfile.profileId,
            repository.loadSetupState().activeProfileId
        )
        assertEquals(false, uiApplied)
        assertTrue(failed)
    }

    private fun createRepository(name: String): ProfileRepository {
        val directory = temporaryFolder.newFolder(name)
        return ProfileRepository(
            profilesFile = File(directory, "profiles.json"),
            setupStateFile = File(directory, "app_setup.json")
        )
    }

    private fun createWorkflow(
        repository: ProfileRepository,
        currentTimeMillis: () -> Long = { 1L },
        firstSetupInputProvider: () -> FirstSetupInput = {
            FirstSetupInput("Default", "skyrim_le", "", false)
        },
        additionalProfileInputProvider: () -> AdditionalProfileInput = {
            AdditionalProfileInput(
                "New Profile",
                "skyrim_le",
                "",
                false
            )
        },
        activeProfileIdProvider: () -> String? = { null },
        dashboardProfileInputProvider: () -> DashboardProfileInput = {
            DashboardProfileInput(
                targetPathText = "",
                rootTargetPathText = "",
                realDeployEnabled = false,
                dataPathReselectionRequired = false,
                rootPathReselectionRequired = false
            )
        },
        applyFirstSetupUiState: (List<GameProfile>, GameProfile) -> Unit = { _, _ -> },
        applyCreatedProfileUiState: (List<GameProfile>, GameProfile) -> Unit = { _, _ -> },
        applySwitchedProfileUiState: (GameProfile) -> Unit = {},
        applySavedProfileUiState: (List<GameProfile>, GameProfile) -> Unit = { _, _ -> },
        applyDeletedProfileUiStateAsync: (List<GameProfile>, GameProfile?) -> Unit = { _, _ -> },
        applyDeletedProfileUiStateBlocking: (List<GameProfile>, GameProfile?) -> Unit = { _, _ -> },
        saveSelectedGameConfigFromUi: () -> Unit = {},
        loadSelectedGameConfigIntoUi: () -> Unit = {},
        recoverActiveProfile: () -> Unit = {},
        syncPluginsFromCurrentState: () -> Unit = {},
        refreshDashboard: () -> Unit = {},
        createSwitchEngine: () -> ProfileSwitchEngine? = { null },
        savePendingEspOrder: () -> Boolean = { true },
        beginOperation: (String) -> Unit = {},
        finishOperation: (String) -> Unit = {},
        failOperation: (String, Throwable?) -> Unit = { _, _ -> },
        closeProfileDialog: () -> Unit = {},
        deleteProfileStorage: (String) -> Boolean = { true },
        appendError: (String) -> Unit = {},
        updateLastOperationStatus: (String) -> Unit = {}
    ): ProfileManagementWorkflow {
        return ProfileManagementWorkflow(
            repositoryProvider = { repository },
            currentTimeMillis = currentTimeMillis,
            gameDisplayNameProvider = { gameId ->
                when (gameId) {
                    "skyrim_le" -> "Skyrim Legendary Edition"
                    "fallout_nv" -> "Fallout New Vegas"
                    else -> gameId
                }
            },
            firstSetupInputProvider = firstSetupInputProvider,
            additionalProfileInputProvider = additionalProfileInputProvider,
            activeProfileIdProvider = activeProfileIdProvider,
            dashboardProfileInputProvider = dashboardProfileInputProvider,
            applyFirstSetupUiState = applyFirstSetupUiState,
            applyCreatedProfileUiState = applyCreatedProfileUiState,
            applySwitchedProfileUiState = applySwitchedProfileUiState,
            applySavedProfileUiState = applySavedProfileUiState,
            applyDeletedProfileUiStateAsync = applyDeletedProfileUiStateAsync,
            applyDeletedProfileUiStateBlocking = applyDeletedProfileUiStateBlocking,
            saveSelectedGameConfigFromUi = saveSelectedGameConfigFromUi,
            loadSelectedGameConfigIntoUi = loadSelectedGameConfigIntoUi,
            recoverActiveProfile = recoverActiveProfile,
            syncPluginsFromCurrentState = syncPluginsFromCurrentState,
            refreshDashboard = refreshDashboard,
            createSwitchEngine = createSwitchEngine,
            savePendingEspOrder = savePendingEspOrder,
            beginOperation = beginOperation,
            finishOperation = finishOperation,
            failOperation = failOperation,
            closeProfileDialog = closeProfileDialog,
            deleteProfileStorage = deleteProfileStorage,
            appendLog = {},
            appendError = appendError,
            updateLastOperationStatus = updateLastOperationStatus
        )
    }

    private fun mod(id: String, enabled: Boolean = true): Mod {
        return Mod(
            id = id,
            name = id,
            installPath = "/mods/$id",
            enabled = enabled,
            priority = 1,
            modType = ModType.LOOSE
        )
    }

    private fun plugin(name: String): PluginEntry {
        return PluginEntry(
            normalizedPath = name.lowercase(),
            pluginName = name,
            sourceModId = "mod",
            sourceModName = "Mod",
            enabled = true,
            priority = 1,
            pluginType = "esp"
        )
    }

    private fun emptyDeploymentResult(): ScopedDeploymentResult {
        val result = DeploymentResult(
            addCount = 0,
            removeCount = 0,
            updateCount = 0,
            finalRecordCount = 0
        )
        return ScopedDeploymentResult(dataResult = result, rootResult = result)
    }

    private inner class FakeSwitchEngine(
        initialMods: List<Mod> = emptyList(),
        initialPlugins: List<PluginEntry> = emptyList(),
        private val events: MutableList<String>,
        private val label: String,
        var failDeployments: Int = 0
    ) : ProfileSwitchEngine {
        var mods: List<Mod> = initialMods
        var plugins: List<PluginEntry> = initialPlugins
        var deployCount: Int = 0
            private set

        override fun getCurrentMods(): List<Mod> {
            events += "$label:get-mods"
            return mods
        }

        override fun saveCurrentMods(mods: List<Mod>) {
            this.mods = mods
            events += if (mods.none { it.enabled }) {
                "$label:disable-mods"
            } else {
                "$label:restore-mods"
            }
        }

        override fun getCurrentPlugins(): List<PluginEntry> {
            events += "$label:get-plugins"
            return plugins
        }

        override fun saveCurrentPlugins(plugins: List<PluginEntry>) {
            this.plugins = plugins
            events += "$label:save-plugins"
        }

        override fun syncPlugins() {
            events += "$label:sync"
        }

        override fun deployForGame(gameId: String): ScopedDeploymentResult {
            deployCount++
            events += "$label:deploy-$gameId"
            if (failDeployments > 0) {
                failDeployments--
                throw IllegalStateException("$label deploy failure")
            }
            return emptyDeploymentResult()
        }
    }

    private fun profile(id: String, name: String, gameId: String): GameProfile {
        return GameProfile(
            profileId = id,
            profileName = name,
            gameId = gameId,
            gameDisplayName = gameId,
            targetDataPath = "",
            targetRootPath = "",
            realDeployEnabled = false,
            iniPresetId = null
        )
    }
}
