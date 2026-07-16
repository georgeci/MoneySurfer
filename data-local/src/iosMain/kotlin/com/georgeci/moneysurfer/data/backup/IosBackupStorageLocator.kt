package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.storage.iosAppStorageDir
import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import okio.Path
import okio.Path.Companion.toPath

private const val MAIN_DB_FILE = "moneysurfer.db"
private const val SYNC_DB_FILE = "moneysurfer_sync.db"
private const val DATASTORE_FILE = "moneysurfer_settings.preferences_pb"

class IosBackupStorageLocator : BackupStorageLocator {
    override val platformName: String = "ios"
    override fun moneySurferDbFile(): Path = "${iosAppStorageDir()}/$MAIN_DB_FILE".toPath()
    override fun syncDbFile(): Path = "${iosAppStorageDir()}/$SYNC_DB_FILE".toPath()
    override fun dataStoreFile(): Path = "${iosAppStorageDir()}/$DATASTORE_FILE".toPath()
}
