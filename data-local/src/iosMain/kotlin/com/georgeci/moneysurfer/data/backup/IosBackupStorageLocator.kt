package com.georgeci.moneysurfer.data.backup

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private const val MAIN_DB_FILE = "moneysurfer.db"
private const val SYNC_DB_FILE = "moneysurfer_sync.db"
private const val DATASTORE_FILE = "moneysurfer_settings.preferences_pb"

class IosBackupStorageLocator : BackupStorageLocator {
    override val platformName: String = "ios"
    override fun moneySurferDbFile(): Path = (documentsDir() + "/$MAIN_DB_FILE").toPath()
    override fun syncDbFile(): Path = (documentsDir() + "/$SYNC_DB_FILE").toPath()
    override fun dataStoreFile(): Path = (documentsDir() + "/$DATASTORE_FILE").toPath()

    private fun documentsDir(): String =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .first() as String
}
