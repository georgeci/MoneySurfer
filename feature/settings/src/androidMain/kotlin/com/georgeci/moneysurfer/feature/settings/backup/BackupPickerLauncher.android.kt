package com.georgeci.moneysurfer.feature.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source

@Composable
actual fun rememberBackupPickerLauncher(
    format: BackupPickerFormat,
    onSavePicked: (BufferedSink?) -> Unit,
    onOpenPicked: (BufferedSource?) -> Unit,
): BackupPickerLauncher {
    val context = LocalContext.current
    val resolver = context.contentResolver

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(format.saveMimeType),
    ) { uri ->
        val sink = uri?.let { resolver.openOutputStream(it)?.sink()?.buffer() }
        onSavePicked(sink)
    }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val source = uri?.let { resolver.openInputStream(it)?.source()?.buffer() }
        onOpenPicked(source)
    }

    return remember(saveLauncher, openLauncher, format) {
        object : BackupPickerLauncher {
            override fun launchSave(suggestedName: String) {
                saveLauncher.launch(suggestedName)
            }
            override fun launchOpen() {
                openLauncher.launch(format.openMimeTypes.toTypedArray())
            }
        }
    }
}
