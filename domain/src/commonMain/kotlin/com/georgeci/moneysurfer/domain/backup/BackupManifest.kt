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
    val encryption: BackupEncryptionInfo? = null,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION: Int = 1
        const val MANIFEST_ENTRY_NAME: String = "manifest.json"
        const val MAIN_DB_ENTRY_NAME: String = "moneysurfer.db"
        const val DATASTORE_ENTRY_NAME: String = "moneysurfer_settings.preferences_pb"
        const val ENCRYPTED_PAYLOAD_ENTRY_NAME: String = "payload.enc"
    }
}

/**
 * Marks a backup whose data entries live inside an encrypted `payload.enc`
 * instead of as plain ZIP entries. Absent (`null`) on unencrypted backups —
 * and omitted from the JSON, so older app versions still parse plain backups.
 */
@Serializable
data class BackupEncryptionInfo(val scheme: String) {
    companion object {
        /** PBKDF2-HMAC-SHA256 key derivation, AES-256-CTR + HMAC-SHA256 encrypt-then-MAC. */
        const val SCHEME_V1: String = "PBKDF2-AES256CTR-HMACSHA256-v1"
    }
}
