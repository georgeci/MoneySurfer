package com.georgeci.moneysurfer.data.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * On-disk location policy for app-internal persistence (Room databases, DataStore) on iOS.
 *
 * Files live in **Application Support**, Apple's recommended location for non-user-visible data,
 * rather than in Documents. Documents becomes user-visible in the Files app the moment
 * `UIFileSharingEnabled` is set, and its contents are included in iCloud/local backups by default.
 *
 * The Application Support directory is configured with an explicit protection policy:
 *  - **excluded from iCloud/local backups** (`NSURLIsExcludedFromBackupKey`): these stores are local
 *    caches that are reconstructible from the sync backend, so backing them up wastes space and risks
 *    restoring a stale copy over newer server data;
 *  - **`NSFileProtectionCompleteUntilFirstUserAuthentication`**: the files are encrypted at rest yet
 *    stay readable for background sync once the device has been unlocked at least once since boot.
 *
 * Both settings are applied at the directory level, so files created inside inherit them — including
 * Room's `-wal`/`-shm` sidecars.
 *
 * NOTE: `sync/default` keeps an intentional copy of this policy in its own `iosMain`
 * (`com.georgeci.moneysurfer.sync.storage.IosSyncAppStorage`) because it cannot depend on this module.
 * Keep the two in sync if the policy changes.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun iosAppStorageFilePath(fileName: String, isDatabase: Boolean): String {
    val fileManager = NSFileManager.defaultManager
    val directoryUrl = requireNotNull(
        fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    ) { "Unable to resolve the Application Support directory" }
    val directoryPath = requireNotNull(directoryUrl.path) {
        "Application Support directory has no filesystem path"
    }

    directoryUrl.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    fileManager.setAttributes(
        mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        ofItemAtPath = directoryPath,
        error = null,
    )

    val targetPath = "$directoryPath/$fileName"
    migrateLegacyDocumentsFile(fileManager, fileName, targetPath, isDatabase)
    return targetPath
}

/**
 * Pure resolution of the Application Support directory, for read-only consumers (e.g. the backup
 * locator) that must point at the same location without triggering migration side effects.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun iosAppStorageDir(): String = requireNotNull(
    NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path,
) { "Unable to resolve the Application Support directory" }

/**
 * Best-effort one-time relocation of a store previously created under Documents (pre-relocation
 * installs) into its new Application Support location. Documents and Application Support share the
 * app container volume, so [NSFileManager.moveItemAtPath] is an atomic rename. On any failure the
 * store is simply recreated fresh in the new location.
 */
@OptIn(ExperimentalForeignApi::class)
private fun migrateLegacyDocumentsFile(
    fileManager: NSFileManager,
    fileName: String,
    targetPath: String,
    isDatabase: Boolean,
) {
    if (fileManager.fileExistsAtPath(targetPath)) return
    val documentsDir = fileManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )?.path ?: return

    // Room keeps the main DB alongside its -wal / -shm sidecars; move them together.
    val suffixes = if (isDatabase) listOf("", "-wal", "-shm") else listOf("")
    suffixes.forEach { suffix ->
        val legacyPath = "$documentsDir/$fileName$suffix"
        val destinationPath = "$targetPath$suffix"
        if (fileManager.fileExistsAtPath(legacyPath) && !fileManager.fileExistsAtPath(destinationPath)) {
            fileManager.moveItemAtPath(legacyPath, destinationPath, error = null)
        }
    }
}
