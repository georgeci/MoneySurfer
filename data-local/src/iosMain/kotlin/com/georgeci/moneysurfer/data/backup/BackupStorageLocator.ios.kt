package com.georgeci.moneysurfer.data.backup

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private const val MAIN_DB_FILE = "moneysurfer.db"
private const val SYNC_DB_FILE = "moneysurfer_sync.db"
private const val DATASTORE_FILE = "moneysurfer_settings.preferences_pb"

actual class BackupStorageLocator {
    actual val platformName: String = "ios"
    actual fun moneySurferDbFile(): Path = (documentsDir() + "/$MAIN_DB_FILE").toPath()
    actual fun syncDbFile(): Path = (documentsDir() + "/$SYNC_DB_FILE").toPath()
    actual fun dataStoreFile(): Path = (documentsDir() + "/$DATASTORE_FILE").toPath()

    private fun documentsDir(): String =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .first() as String
}
