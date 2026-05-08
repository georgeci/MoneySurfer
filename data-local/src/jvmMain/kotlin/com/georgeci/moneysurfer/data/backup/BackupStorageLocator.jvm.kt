package com.georgeci.moneysurfer.data.backup

import okio.Path
import okio.Path.Companion.toPath
import java.io.File

private const val MAIN_DB_FILE = "moneysurfer.db"
private const val SYNC_DB_FILE = "moneysurfer_sync.db"
private const val DATASTORE_FILE = "moneysurfer_settings.preferences_pb"
private const val APP_DIR = ".moneysurfer"

actual class BackupStorageLocator {
    actual val platformName: String = "jvm"
    actual fun moneySurferDbFile(): Path = appFile(MAIN_DB_FILE).toPath()
    actual fun syncDbFile(): Path = appFile(SYNC_DB_FILE).toPath()
    actual fun dataStoreFile(): Path = appFile(DATASTORE_FILE).toPath()

    private fun appFile(name: String): String =
        File(System.getProperty("java.io.tmpdir"), "$APP_DIR/$name").absolutePath
}
