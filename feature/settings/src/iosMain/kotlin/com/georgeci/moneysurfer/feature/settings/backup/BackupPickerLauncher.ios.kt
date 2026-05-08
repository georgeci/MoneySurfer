package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import okio.BufferedSink
import okio.BufferedSource

/**
 * iOS picker is a deliberate stub for this iteration.
 *
 * UIDocumentPicker is asymmetric with our cross-platform contract — it copies
 * an existing file rather than producing a sink — so a correct implementation
 * needs a temp-file ForwardingSink trick plus the UIKit delegate dance, which
 * is risky to land without the iOS toolchain in CI. The Android + JVM paths
 * cover both validation flows; iOS file pickers will land in a follow-up.
 *
 * The stub returns `null` from both pickers, which surfaces as silent
 * "cancelled" in the ViewModel — the user sees no progress and nothing
 * happens. That's the least-surprising failure mode while we keep the build
 * green across all targets.
 */
@Composable
actual fun rememberBackupPickerLauncher(
    onSavePicked: (BufferedSink?) -> Unit,
    onOpenPicked: (BufferedSource?) -> Unit,
): BackupPickerLauncher = remember(onSavePicked, onOpenPicked) {
    object : BackupPickerLauncher {
        override fun launchSave(@Suppress("UNUSED_PARAMETER") suggestedName: String) =
            onSavePicked(null)
        override fun launchOpen() = onOpenPicked(null)
    }
}
