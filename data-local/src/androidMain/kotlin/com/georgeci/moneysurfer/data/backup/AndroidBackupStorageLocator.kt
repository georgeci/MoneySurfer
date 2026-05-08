package com.georgeci.moneysurfer.data.backup

import android.content.Context
import okio.Path
import okio.Path.Companion.toPath

private const val MAIN_DB_FILE = "moneysurfer.db"
private const val SYNC_DB_FILE = "moneysurfer_sync.db"
private const val DATASTORE_FILE = "moneysurfer_settings.preferences_pb"

class AndroidBackupStorageLocator(private val context: Context) : BackupStorageLocator {
    override val platformName: String = "android"
    override fun moneySurferDbFile(): Path = context.getDatabasePath(MAIN_DB_FILE).absolutePath.toPath()
    override fun syncDbFile(): Path = context.getDatabasePath(SYNC_DB_FILE).absolutePath.toPath()
    override fun dataStoreFile(): Path =
        context.filesDir.resolve(DATASTORE_FILE).absolutePath.toPath()
}
