package com.georgeci.moneysurfer.data.backup

sealed class BackupError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    data object PickerCancelled : BackupError("File picker cancelled")
    data class Io(val ioCause: Throwable) : BackupError("I/O error: ${ioCause.message}", ioCause)
    data class InvalidArchive(val reason: String) : BackupError("Invalid backup archive: $reason")
    data class FormatMismatch(val expected: Int, val actual: Int) :
        BackupError("Backup format v$actual is not supported (expected v$expected)")
    data class SchemaMismatch(val expected: Int, val actual: Int) :
        BackupError("Backup was made with database schema v$actual; this app uses v$expected")
    data class MissingFile(val name: String) : BackupError("Backup is missing required entry: $name")
    data class CrcMismatch(val name: String) : BackupError("CRC32 mismatch for entry: $name")
}
