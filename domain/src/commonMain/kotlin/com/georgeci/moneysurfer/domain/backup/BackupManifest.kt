package com.georgeci.moneysurfer.domain.backup

import kotlinx.serialization.Serializable

/**
 * Self-describing header of a backup ZIP — written as the first entry
 * (`manifest.json`) and read before any file extraction so the importer can
 * reject incompatible payloads up front.
 */
@Serializable
data class BackupManifest(
    val backupFormatVersion: Int,
    val moneySurferDbVersion: Int,
    val appVersion: String,
    val appVersionCode: Int,
    val sourcePlatform: String,
    val createdAtEpochMillis: Long,
    val files: List<String>,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION: Int = 1
        const val MANIFEST_ENTRY_NAME: String = "manifest.json"
        const val MAIN_DB_ENTRY_NAME: String = "moneysurfer.db"
        const val DATASTORE_ENTRY_NAME: String = "moneysurfer_settings.preferences_pb"
    }
}
