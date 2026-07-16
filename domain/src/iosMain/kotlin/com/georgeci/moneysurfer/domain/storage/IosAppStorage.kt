package com.georgeci.moneysurfer.domain.storage

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

private const val TAG = "IosAppStorage"

/**
 * On-disk location policy for app-internal persistence (Room databases, DataStore) on iOS.
 *
 * This is the single source of truth for where those stores live and how they are protected; the
 * `data-local` and `sync/default` iOS source sets both call into it (they cannot depend on each
 * other, but both depend on `domain`).
 *
 * Files live in **Application Support**, Apple's recommended location for non-user-visible data,
 * rather than in Documents. Documents becomes user-visible in the Files app the moment
 * `UIFileSharingEnabled` is set, and its contents are included in iCloud/local backups by default.
 *
 * The Application Support directory is configured with an explicit protection policy:
 *  - **excluded from iCloud/local backups** (`NSURLIsExcludedFromBackupKey`): everything here is
 *    app-internal, non-user-visible state that we deliberately keep out of backups. The Room DBs are
 *    reconstructible from the sync backend; the DataStore holds only local UI/session preferences
 *    (theme, onboarding flags, selected workspace) whose loss on a device restore is acceptable —
 *    they fall back to defaults and re-sync — and which we would rather not resurrect stale from an
 *    old backup on top of newer server state.
 *  - **`NSFileProtectionCompleteUntilFirstUserAuthentication`**: the files are encrypted at rest yet
 *    stay readable for background sync once the device has been unlocked at least once since boot.
 *
 * Both settings are applied at the directory level, so files created inside inherit them — including
 * Room's `-wal`/`-shm` sidecars. A failure to apply either is logged (the OS should never reject it
 * for our own container, but the policy is the point of this code, so we surface it rather than fail
 * app startup over a hardening attribute).
 */
@OptIn(ExperimentalForeignApi::class)
public fun iosAppStorageFilePath(fileName: String, isDatabase: Boolean): String {
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

    applyProtectionPolicy(fileManager, directoryUrl, directoryPath)

    val targetPath = "$directoryPath/$fileName"
    migrateLegacyDocumentsFile(fileManager, fileName, targetPath, isDatabase)
    return targetPath
}

/**
 * Pure resolution of the Application Support directory, for read-only consumers (e.g. the backup
 * locator) that must point at the same location without triggering migration side effects.
 */
@OptIn(ExperimentalForeignApi::class)
public fun iosAppStorageDir(): String = requireNotNull(
    NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path,
) { "Unable to resolve the Application Support directory" }

/**
 * Applies the backup-exclusion and file-protection attributes to the storage directory, logging a
 * warning (with the underlying [NSError]) if the OS rejects either — the attributes are the security
 * point of this change, so a silent failure must not go unnoticed.
 */
@OptIn(ExperimentalForeignApi::class)
private fun applyProtectionPolicy(
    fileManager: NSFileManager,
    directoryUrl: NSURL,
    directoryPath: String,
) = memScoped {
    val backupError = alloc<ObjCObjectVar<NSError?>>()
    val excluded = directoryUrl.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = backupError.ptr)
    if (!excluded) {
        Logger.withTag(TAG).w { "Failed to exclude app storage from backups: ${backupError.value}" }
    }

    val protectionError = alloc<ObjCObjectVar<NSError?>>()
    val protected = fileManager.setAttributes(
        mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        ofItemAtPath = directoryPath,
        error = protectionError.ptr,
    )
    if (!protected) {
        Logger.withTag(TAG).w { "Failed to set file protection on app storage: ${protectionError.value}" }
    }
}

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
