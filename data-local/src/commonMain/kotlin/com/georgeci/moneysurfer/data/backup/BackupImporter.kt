package com.georgeci.moneysurfer.data.backup

import okio.BufferedSource

interface BackupImporter {
    /**
     * Reads a backup ZIP from [source] and replaces the current local data.
     *
     * On success the caller must immediately trigger an app restart — the in-memory
     * Koin singletons still reference closed Room handles. Caller owns [source]
     * and must close it.
     */
    suspend fun importFrom(source: BufferedSource): Result<BackupManifest>
}
