package com.georgeci.moneysurfer.data.backup

import okio.Path

/** Per-platform locator for the files that participate in a backup. */
interface BackupStorageLocator {
    val platformName: String
    fun moneySurferDbFile(): Path
    fun syncDbFile(): Path
    fun dataStoreFile(): Path
}
