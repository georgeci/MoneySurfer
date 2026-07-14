package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberBackupPickerLauncher(
    onSavePicked: (BufferedSink?) -> Unit,
    onOpenPicked: (BufferedSource?) -> Unit,
): BackupPickerLauncher = remember(onSavePicked, onOpenPicked) {
    object : BackupPickerLauncher {
        override fun launchSave(suggestedName: String) {
            val file = chooseFile(mode = FileDialog.SAVE, defaultName = suggestedName)
            val sink = file?.let { it.sink().buffer() }
            onSavePicked(sink)
        }

        override fun launchOpen() {
            val file = chooseFile(mode = FileDialog.LOAD)
            val source = file?.let { it.source().buffer() }
            onOpenPicked(source)
        }
    }
}

private fun chooseFile(mode: Int, defaultName: String? = null): File? {
    val dialog = FileDialog(null as Frame?, "MoneySurfer backup", mode)
    if (defaultName != null) dialog.file = defaultName
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}
