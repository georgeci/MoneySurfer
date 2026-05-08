package com.georgeci.moneysurfer.data.backup

import kotlinx.serialization.Serializable

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
