package com.georgeci.moneysurfer.domain.backup

import okio.Path

/**
 * Locates the on-disk files that participate in a backup, per platform.
 * Implementations live in `:data-local` (`{Android,Ios,Jvm}BackupStorageLocator`)
 * and are bound to this interface in `SharedPlatformModule`.
 */
interface BackupStorageLocator {
    val platformName: String
    fun moneySurferDbFile(): Path
    fun syncDbFile(): Path
    fun dataStoreFile(): Path
}

/**
 * Restarts the host process so newly-imported on-disk state is observed.
 *
 * iOS implementations cannot relaunch programmatically; they exit cleanly
 * and the calling layer surfaces a "please reopen" message.
 */
interface AppRestarter {
    fun restart()
}
