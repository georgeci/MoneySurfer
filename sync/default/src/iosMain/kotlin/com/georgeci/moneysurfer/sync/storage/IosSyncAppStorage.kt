package com.georgeci.moneysurfer.sync.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * On-disk location policy for the sync Room database on iOS.
 *
 * The sync DB lives in **Application Support** (Apple's recommended location for non-user-visible
 * data) rather than in Documents, is **excluded from iCloud/local backups**, and is assigned the
 * **`NSFileProtectionCompleteUntilFirstUserAuthentication`** data-protection class so it stays
 * readable for background sync after the first unlock. See the class doc for the rationale.
 *
 * NOTE: this is an intentional copy of the policy in `data-local`'s `iosMain`
 * (`com.georgeci.moneysurfer.data.storage.IosAppStorage`); this module cannot depend on `data-local`.
 * Keep the two in sync if the policy changes.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun iosSyncStorageFilePath(fileName: String): String {
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
    migrateLegacyDocumentsDb(fileManager, fileName, targetPath)
    return targetPath
}

/**
 * Best-effort one-time relocation of a sync DB previously created under Documents into its new
 * Application Support location. The two directories share the app container volume, so the move is
 * an atomic rename; on any failure the DB is recreated fresh.
 */
@OptIn(ExperimentalForeignApi::class)
private fun migrateLegacyDocumentsDb(
    fileManager: NSFileManager,
    fileName: String,
    targetPath: String,
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
    listOf("", "-wal", "-shm").forEach { suffix ->
        val legacyPath = "$documentsDir/$fileName$suffix"
        val destinationPath = "$targetPath$suffix"
        if (fileManager.fileExistsAtPath(legacyPath) && !fileManager.fileExistsAtPath(destinationPath)) {
            fileManager.moveItemAtPath(legacyPath, destinationPath, error = null)
        }
    }
}
