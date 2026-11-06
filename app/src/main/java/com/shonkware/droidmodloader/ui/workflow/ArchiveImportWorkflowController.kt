package com.shonkware.droidmodloader.ui.workflow

import android.net.Uri
import java.io.File

internal class ArchiveImportWorkflowController(
    private val appendLog: (String) -> Unit,
    private val runInBackground: (() -> Unit) -> Boolean,
    private val handleImportedArchive: (File) -> Unit,
    private val handleImportedArchiveUri: (Uri, String) -> Unit,
    private val showArchiveLibrarySummary: () -> Unit
) {
    /** Legacy filesystem entry point. The UI now uses the system document picker. */
    fun handleArchivePath(path: String?): Boolean {
        val archive = path
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)

        if (archive == null) {
            appendLog("No archive selected.")
            return false
        }

        return runInBackground {
            handleImportedArchive(archive)
        }
    }

    fun handleImportedUri(uri: Uri, displayName: String): Boolean {
        return runInBackground {
            handleImportedArchiveUri(uri, displayName)
        }
    }

    fun requestArchiveLibrarySummary() {
        runInBackground {
            showArchiveLibrarySummary()
        }
    }
}
