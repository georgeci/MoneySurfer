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
    /**
     * `true` when [restart] only terminates the process and the user has to
     * open the app again by hand (iOS). Callers must show that as an explicit
     * message *before* invoking [restart], or a successful restore looks like
     * a crash.
     */
    val requiresManualRelaunch: Boolean get() = false

    fun restart()
}
