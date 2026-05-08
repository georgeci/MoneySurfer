package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.runtime.Composable
import okio.BufferedSink
import okio.BufferedSource

/**
 * Hides the platform-specific file picker behind a Composable factory.
 *
 * The Composable returns a [BackupPickerLauncher] backed by:
 *  - Android: `ActivityResultContracts.CreateDocument` / `OpenDocument` via `rememberLauncherForActivityResult`
 *  - iOS: `UIDocumentPickerViewController`
 *  - JVM: `java.awt.FileDialog`
 *
 * On all platforms, completion (or cancellation) calls [onSavePicked] / [onOpenPicked]
 * with the chosen sink/source, or `null` when the user cancelled.
 */
@Composable
expect fun rememberBackupPickerLauncher(
    onSavePicked: (BufferedSink?) -> Unit,
    onOpenPicked: (BufferedSource?) -> Unit,
): BackupPickerLauncher

interface BackupPickerLauncher {
    fun launchSave(suggestedName: String)
    fun launchOpen()
}
