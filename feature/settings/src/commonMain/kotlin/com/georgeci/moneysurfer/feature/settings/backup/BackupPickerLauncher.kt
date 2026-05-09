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
 *  - **iOS**: currently a no-op stub (see `BackupPickerLauncher.ios.kt`).
 *    The `UIDocumentPicker` flow needs a temp-file ForwardingSink trick plus
 *    UIKit delegate plumbing that we'd rather land with the iOS toolchain
 *    in CI; the Android + JVM paths cover validation in the meantime.
 *
 * Completion (or cancellation) calls [onSavePicked] / [onOpenPicked] with the
 * chosen sink/source, or `null` when the user cancelled.
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
