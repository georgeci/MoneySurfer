package com.georgeci.moneysurfer.domain.backup

/**
 * Domain-level errors that surface from [BackupExporter] / [BackupImporter].
 * Wrapping raw `Throwable`s at the data-layer boundary keeps callers free of
 * SDK / I/O exception types per AGENTS.md.
 */
sealed class BackupError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    data object PickerCancelled : BackupError("File picker cancelled")
    data class Io(val ioCause: Throwable) : BackupError("I/O error: ${ioCause.message}", ioCause)
    data class InvalidArchive(val reason: String, val parseCause: Throwable? = null) :
        BackupError("Invalid backup archive: $reason", parseCause)
    data class FormatMismatch(val expected: Int, val actual: Int) :
        BackupError("Backup format v$actual is not supported (expected v$expected)")
    data class SchemaMismatch(val expected: Int, val actual: Int) :
        BackupError("Backup was made with database schema v$actual; this app uses v$expected")
    data class MissingFile(val name: String) : BackupError("Backup is missing required entry: $name")
    data class CrcMismatch(val name: String) : BackupError("CRC32 mismatch for entry: $name")
    data object PassphraseRequired : BackupError("Backup is encrypted; a passphrase is required")
    data object WrongPassphrase : BackupError("Wrong passphrase, or the encrypted backup is corrupted")
    data class CorruptDatabase(val reason: String, val checkCause: Throwable? = null) :
        BackupError("Restored database failed integrity check: $reason", checkCause)
}
