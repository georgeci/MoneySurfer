package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import okio.BufferedSink
import okio.BufferedSource

/**
 * **iOS stub — not wired to UIDocumentPicker yet.**
 *
 * `UIDocumentPicker` is asymmetric with our cross-platform contract — it copies
 * an existing file rather than producing a sink — so a correct implementation
 * needs a temp-file ForwardingSink trick plus the UIKit delegate dance, which
 * we'd rather land with the iOS toolchain in CI to verify. The Android + JVM
 * paths cover both validation flows for this iteration; iOS picker support
 * is planned as a follow-up.
 *
 * The stub returns `null` from both pickers, which surfaces as silent
 * "cancelled" in the ViewModel (no toast, no progress) — the least-surprising
 * failure mode while the build stays green across all targets.
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
