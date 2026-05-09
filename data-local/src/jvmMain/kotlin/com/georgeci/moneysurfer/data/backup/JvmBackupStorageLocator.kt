package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import okio.Path
import okio.Path.Companion.toPath
import java.io.File

private const val MAIN_DB_FILE = "moneysurfer.db"
private const val SYNC_DB_FILE = "moneysurfer_sync.db"
private const val DATASTORE_FILE = "moneysurfer_settings.preferences_pb"
private const val APP_DIR = ".moneysurfer"

class JvmBackupStorageLocator : BackupStorageLocator {
    override val platformName: String = "jvm"
    override fun moneySurferDbFile(): Path = appFile(MAIN_DB_FILE).toPath()
    override fun syncDbFile(): Path = appFile(SYNC_DB_FILE).toPath()
    override fun dataStoreFile(): Path = appFile(DATASTORE_FILE).toPath()

    private fun appFile(name: String): String =
        File(System.getProperty("java.io.tmpdir"), "$APP_DIR/$name").absolutePath
}
