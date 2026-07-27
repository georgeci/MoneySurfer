package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.runtime.Composable
import okio.BufferedSink
import okio.BufferedSource

/**
 * Hides the platform-specific file picker behind a Composable factory.
 *
 * Backed per platform by:
 *  - **Android**: `ActivityResultContracts.CreateDocument` / `OpenDocument`
 *    via `rememberLauncherForActivityResult`.
 *  - **JVM**: `java.awt.FileDialog`.
 *  - **iOS**: `UIDocumentPickerViewController`, staging through a temp file in
 *    both directions (see `BackupPickerLauncher.ios.kt`).
 *
 * Completion (or cancellation) calls [onSavePicked] / [onOpenPicked] with the
 * chosen sink/source, or `null` when the user cancelled.
 */
@Composable
expect fun rememberBackupPickerLauncher(
    format: BackupPickerFormat,
    onSavePicked: (BufferedSink?) -> Unit,
    onOpenPicked: (BufferedSource?) -> Unit,
): BackupPickerLauncher

interface BackupPickerLauncher {
    fun launchSave(suggestedName: String)
    fun launchOpen()

    /**
     * Reports how the write into the last [launchSave] sink ended.
     *
     * **Every screen that calls [launchSave] must call this exactly once per
     * export**, as soon as it knows the outcome — on iOS the archive is staged
     * in a temp file and the "where do I put it" picker only appears from here.
     * Closing the sink cannot stand in for it: an aborted export closes its sink
     * too, and acting on that would offer the user a truncated archive.
     *
     * No-op on Android and JVM, where the picker already chose the destination
     * before a single byte was written.
     */
    fun onSaveCompleted(succeeded: Boolean) = Unit
}

/**
 * File format the picker is launched for. Android consumes the MIME types
 * directly (SAF document contracts) and iOS resolves them to uniform type
 * identifiers; JVM's `FileDialog` ignores them. `application/octet-stream`
 * stays in the open list because some providers report generic types for
 * perfectly valid files.
 */
enum class BackupPickerFormat(
    val saveMimeType: String,
    val openMimeTypes: List<String>,
) {
    Zip(
        saveMimeType = "application/zip",
        openMimeTypes = listOf("application/zip", "application/octet-stream"),
    ),
    Csv(
        saveMimeType = "text/csv",
        openMimeTypes = listOf(
            "text/csv",
            "text/comma-separated-values",
            "text/plain",
            "application/octet-stream",
        ),
    ),
}
