package com.shonkware.droidmodloader.ui.workflow

import android.net.Uri
import com.shonkware.droidmodloader.engine.ModEngine
import com.shonkware.droidmodloader.engine.download.DownloadedArchiveRecord
import com.shonkware.droidmodloader.engine.install.ArchiveFormatProbe
import com.shonkware.droidmodloader.engine.install.ArchiveFormatProbeException
import com.shonkware.droidmodloader.engine.install.ArchiveReadException
import com.shonkware.droidmodloader.engine.install.ArchiveReaderRegistry
import com.shonkware.droidmodloader.engine.install.InstallCancellationController
import com.shonkware.droidmodloader.engine.install.InstallCancellationSignal
import com.shonkware.droidmodloader.engine.install.InstallCancelledException
import com.shonkware.droidmodloader.engine.install.PreparedArchiveInstall
import com.shonkware.droidmodloader.engine.io.ArchiveImportFileStore
import com.shonkware.droidmodloader.engine.model.Mod
import com.shonkware.droidmodloader.ui.archive.ArchiveInspectionReport
import com.shonkware.droidmodloader.ui.archive.archiveFailureMessage
import java.io.File
import java.util.concurrent.atomic.AtomicReference

internal interface ArchiveImportEngine {
    fun registerDownloadedArchive(
        archiveFile: File,
        originalDisplayName: String,
        sourcePath: String?
    ): DownloadedArchiveRecord

    fun getInstalledModsFromFolders(): List<Mod>

    fun prepareArchiveInstall(
        archive: File,
        cancellationSignal: InstallCancellationSignal
    ): PreparedArchiveInstall

    fun finalizePreparedArchiveInstall(
        prepared: PreparedArchiveInstall,
        selectedOptionIds: Set<String>,
        priority: Int,
        sourceType: String,
        cancellationSignal: InstallCancellationSignal
    ): Mod

    fun markDownloadedArchiveInstalled(
        archiveId: String,
        installedModId: String
    )

    fun getCurrentMods(): List<Mod>

    fun saveCurrentMods(mods: List<Mod>)

    fun syncPlugins()

    fun appendInstalledModRoutingSummary(mod: Mod)

    fun cancelPreparedArchiveInstall(
        prepared: PreparedArchiveInstall
    )

    fun findExistingModName(name: String): String?
}

internal class ArchiveImportEngineAdapter(
    private val engine: ModEngine,
    private val syncPluginsFromCurrentState: () -> Unit,
    private val appendRoutingSummary: (Mod) -> Unit
) : ArchiveImportEngine {
    override fun registerDownloadedArchive(
        archiveFile: File,
        originalDisplayName: String,
        sourcePath: String?
    ): DownloadedArchiveRecord {
        return engine.registerDownloadedArchive(
            archiveFile = archiveFile,
            originalDisplayName = originalDisplayName,
            sourcePath = sourcePath
        )
    }

    override fun getInstalledModsFromFolders(): List<Mod> {
        return engine.getInstalledModsFromFolders()
    }

    override fun prepareArchiveInstall(
        archive: File,
        cancellationSignal: InstallCancellationSignal
    ): PreparedArchiveInstall {
        return engine.prepareArchiveInstall(
            archive = archive,
            cancellationSignal = cancellationSignal
        )
    }

    override fun finalizePreparedArchiveInstall(
        prepared: PreparedArchiveInstall,
        selectedOptionIds: Set<String>,
        priority: Int,
        sourceType: String,
        cancellationSignal: InstallCancellationSignal
    ): Mod {
        return engine.finalizePreparedArchiveInstall(
            prepared = prepared,
            selectedOptionIds = selectedOptionIds,
            priority = priority,
            sourceType = sourceType,
            cancellationSignal = cancellationSignal
        )
    }

    override fun markDownloadedArchiveInstalled(
        archiveId: String,
        installedModId: String
    ) {
        engine.markDownloadedArchiveInstalled(
            archiveId = archiveId,
            installedModId = installedModId
        )
    }

    override fun getCurrentMods(): List<Mod> {
        return engine.getCurrentMods()
    }

    override fun saveCurrentMods(mods: List<Mod>) {
        engine.saveCurrentMods(mods)
    }

    override fun syncPlugins() {
        syncPluginsFromCurrentState()
    }

    override fun appendInstalledModRoutingSummary(mod: Mod) {
        appendRoutingSummary(mod)
    }

    override fun cancelPreparedArchiveInstall(
        prepared: PreparedArchiveInstall
    ) {
        engine.cancelPreparedArchiveInstall(prepared)
    }

    override fun findExistingModName(name: String): String? {
        return engine.findExistingModName(name)
    }
}

internal class ArchiveImportExecutionWorkflow(
    private val operationInProgressProvider: () -> Boolean,
    private val beginOperation: (String) -> OperationAttempt,
    private val createEngine: () -> ArchiveImportEngine?,
    private val archiveImportFileStore: ArchiveImportFileStore,
    private val showInstallerChoices:
        (PreparedArchiveInstall, String?, OperationAttempt, Boolean) -> Unit,
    private val appendLog: (String) -> Unit,
    private val appendError: (String, Throwable?) -> Unit,
    private val updateLastOperationStatus: (String) -> Unit,
    private val refreshDashboard: () -> Unit,
    private val updateArchiveImportInProgress:
        (Boolean) -> Unit,
    private val archiveFormatProbe:
        ArchiveFormatProbe = ArchiveFormatProbe(),
    private val archiveReaderRegistry:
        ArchiveReaderRegistry = ArchiveReaderRegistry(),
    private val inspectionPublisher: (String) -> (ArchiveInspectionReport) -> Unit = { {} },
    private val showToast: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val activeCancellationController =
        AtomicReference<InstallCancellationController?>(null)


    fun importArchive(sourceFile: File) {
        importArchiveSource(sourceFile = sourceFile, sourceUri = null, displayName = sourceFile.name)
    }

    /** Imports a document returned by a system file picker, copying it straight to staging. */
    fun importArchive(sourceUri: Uri, displayName: String) {
        importArchiveSource(sourceFile = null, sourceUri = sourceUri, displayName = displayName)
    }

    private fun importArchiveSource(sourceFile: File?, sourceUri: Uri?, displayName: String) {
        if (operationInProgressProvider()) {
            appendLog(
                "Ignoring import request: operation already in progress."
            )
            return
        }

        val cancellationController =
            InstallCancellationController()

        if (
            !activeCancellationController.compareAndSet(
                null,
                cancellationController
            )
        ) {
            appendLog(
                "Ignoring import request: archive import is already active."
            )
            return
        }
        updateArchiveImportInProgress(true)

        var engine: ArchiveImportEngine? = null
        var archiveSnapshotFile: File? = null
        var prepared: PreparedArchiveInstall? = null
        var operation: OperationAttempt? = null
        var publishInspection: (ArchiveInspectionReport) -> Unit = {}
        val fileName = displayName.takeIf { it.isNotBlank() } ?: sourceFile?.name ?: "imported_mod"
        val sourcePath = sourceFile?.absolutePath ?: sourceUri?.toString().orEmpty()

        try {
            operation = beginOperation("Importing archive...")
            publishInspection = inspectionPublisher(sourcePath)

            cancellationController.signal
                .throwIfCancellationRequested()

            val sanitizedName =
                sanitizeArchiveDisplayName(fileName)

            val copiedArchiveFile = if (sourceUri != null) {
                archiveImportFileStore.copyUriToTemporarySnapshot(
                    uri = sourceUri,
                    displayName = sanitizedName,
                    cancellationSignal = cancellationController.signal
                )
            } else {
                archiveImportFileStore.copyToTemporarySnapshot(
                    sourceFile = checkNotNull(sourceFile),
                    displayName = sanitizedName,
                    cancellationSignal = cancellationController.signal
                )
            }
            archiveSnapshotFile = copiedArchiveFile

            val sourceProbe =
                archiveFormatProbe.probe(copiedArchiveFile)

            appendLog(
                "Archive preflight format: " +
                        sourceProbe.format.diagnosticLabel
            )

            if (sourceProbe.extensionMismatch) {
                appendLog(
                    "ARCHIVE WARNING: File extension does not match " +
                            "detected " +
                            sourceProbe.format.diagnosticLabel +
                            " content."
                )
            }

            archiveReaderRegistry.findReader(
                format = sourceProbe.format,
                archiveName = fileName
            )

            cancellationController.signal
                .throwIfCancellationRequested()

            engine = createEngine()

            if (engine == null) {
                val message = "Import archive failed: engine could not be created."
                publishInspection(ArchiveInspectionReport(fileName, clock(), message = message,
                    attemptId = operation.snapshot().id))
                operation.fail(message)
                showToast(message)
                return
            }

            appendLog(
                "Temporary archive staging copy prepared: " +
                        copiedArchiveFile.name
            )
            appendLog(
                "Archive format: " +
                        sourceProbe.format.diagnosticLabel
            )
            appendLog(
                "Archive size: " +
                        copiedArchiveFile.length() +
                        " bytes"
            )
            appendLog(
                "This archive is not added to a permanent library. It is removed after preparation."
            )

            val preparedInstall =
                engine.prepareArchiveInstall(
                    archive = copiedArchiveFile,
                    cancellationSignal =
                        cancellationController.signal
                )

            prepared = preparedInstall
            cancellationController.signal.throwIfCancellationRequested()
            preparedInstall.inspection?.let { inspection ->
                publishInspection(ArchiveInspectionReport(
                    fileName, inspection.extraction.observedAtMillis, inspection,
                    attemptId = operation.snapshot().id
                ))
            }

            // Every archive gets the same opportunity to name the mod and choose files.
            // Warn early if a mod with this name already exists.
            val existingModWarning = engine.findExistingModName(preparedInstall.modName) != null
            showInstallerChoices(preparedInstall, null, operation, existingModWarning)
            operation.waitForChoices("Choose installer options.")
        } catch (exception: InstallCancelledException) {
            exception.suppressed.forEach { cleanupFailure ->
                appendError("Archive import cleanup failed: ${cleanupFailure.message}", cleanupFailure)
            }
            val activeEngine = engine
            val activePrepared = prepared

            if (activePrepared != null && activeEngine != null) {
                try {
                    activeEngine.cancelPreparedArchiveInstall(
                        activePrepared
                    )
                } catch (cleanupException: Exception) {
                    exception.addSuppressed(cleanupException)
                    appendError(
                        "Failed to clean cancelled archive " +
                                "installer session: " +
                                cleanupException.message,
                        cleanupException
                    )
                }
            }

            appendLog(
                "Archive import cancellation completed."
            )
            appendLog("RESULT: CANCELLED")

            operation?.cancel("Archive import cancelled.")
        } catch (exception: ArchiveFormatProbeException) {
            handleArchiveFailure(
                operation = operation,
                failureCode = exception.code.name,
                message = archiveFailureMessage(exception.code),
                exception = exception,
                archiveName = fileName,
                publishInspection = publishInspection
            )
        } catch (exception: ArchiveReadException) {
            handleArchiveFailure(
                operation = operation,
                failureCode = exception.code.name,
                message = archiveFailureMessage(exception.code),
                exception = exception,
                archiveName = fileName,
                publishInspection = publishInspection
            )
        } catch (throwable: Throwable) {
            appendLog(
                "CRASH TYPE: " +
                        throwable::class.java.name
            )
            appendLog("RESULT: FAIL")

            val message = "Import archive failed: ${throwable.message}"
            publishInspection(ArchiveInspectionReport(fileName, clock(), message = message,
                attemptId = operation?.snapshot()?.id))
            operation?.fail(message, throwable)
            showToast(message)
        } finally {
            archiveSnapshotFile?.let { snapshot ->
                try {
                    archiveImportFileStore.releaseSnapshot(snapshot)
                } catch (cleanupFailure: Exception) {
                    appendError(
                        "Temporary archive cleanup failed; startup will retry: ${cleanupFailure.message}",
                        cleanupFailure
                    )
                }
            }
            activeCancellationController.compareAndSet(
                cancellationController,
                null
            )

            updateArchiveImportInProgress(false)

            refreshDashboard()

            appendLog(
                "----- Import Archive Workflow End -----"
            )
        }
    }

    fun cancelImport() {
        if (!cancelActiveImport()) {
            appendLog(
                "Ignoring archive import cancellation request: " +
                    "no archive import is active."
            )
        }
    }

    fun cancelActiveImport(): Boolean {
        val cancellationController = activeCancellationController.get() ?: return false
        cancellationController.cancel()

        updateLastOperationStatus(
            "Cancelling archive import..."
        )
        appendLog(
            "Archive import cancellation requested."
        )
        return true
    }

    private fun handleArchiveFailure(
        operation: OperationAttempt?,
        failureCode: String,
        message: String,
        exception: Exception,
        archiveName: String,
        publishInspection: (ArchiveInspectionReport) -> Unit
    ) {
        appendLog(
            "ARCHIVE FAILURE: $failureCode"
        )
        appendLog("RESULT: FAIL")

        appendLog("Archive failure details: ${exception.message.orEmpty()}")
        exception.suppressed.forEach { cleanupFailure ->
            appendError("Archive cleanup failed: ${cleanupFailure.message}", cleanupFailure)
        }
        publishInspection(ArchiveInspectionReport(archiveName, clock(), failureCode = failureCode,
            message = message, attemptId = operation?.snapshot()?.id))
        operation?.fail("Import archive failed: $message", null)
        showToast("Import failed: $message")
    }

}

internal fun sanitizeArchiveDisplayName(
    displayName: String
): String {
    return displayName.replace(
        Regex("""[^\w.\- ]"""),
        "_"
    )
}

internal fun calculateNextArchivePriority(
    existingMods: List<Mod>
): Int {
    return if (existingMods.isEmpty()) {
        1
    } else {
        existingMods.maxOf {
            it.priority
        } + 1
    }
}
