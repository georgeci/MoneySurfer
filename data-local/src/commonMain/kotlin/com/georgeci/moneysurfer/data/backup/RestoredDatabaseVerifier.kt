package com.georgeci.moneysurfer.data.backup

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.georgeci.moneysurfer.domain.backup.BackupError
import okio.FileSystem
import okio.Path

/**
 * Rejects a restored SQLite file before it ever replaces the live one:
 * header magic first (cheap, avoids handing arbitrary bytes to the SQLite
 * parser), then `PRAGMA integrity_check` through the bundled (current)
 * SQLite build on a read-only connection.
 */
internal object RestoredDatabaseVerifier {

    fun verify(fs: FileSystem, path: Path) {
        if (!hasSqliteMagic(fs, path)) {
            throw BackupError.CorruptDatabase("not an SQLite database file")
        }
        val verdict = runIntegrityCheck(path)
        if (verdict != INTEGRITY_OK) {
            throw BackupError.CorruptDatabase(verdict.take(MAX_REASON_LENGTH))
        }
    }

    private fun hasSqliteMagic(fs: FileSystem, path: Path): Boolean = runCatching {
        fs.read(path) { readByteArray(SQLITE_MAGIC.size.toLong()) }.contentEquals(SQLITE_MAGIC)
    }.getOrDefault(false)

    /**
     * Returns the first `PRAGMA integrity_check` result row ("ok" for a
     * healthy file); any SQLite-level failure to open or query the file is
     * itself a corruption verdict.
     */
    private fun runIntegrityCheck(path: Path): String = try {
        val connection = BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READONLY)
        try {
            firstIntegrityRow(connection)
        } finally {
            connection.close()
        }
    } catch (
        @Suppress("TooGenericExceptionCaught") t: Throwable,
    ) {
        throw BackupError.CorruptDatabase("integrity_check failed: ${t.message}", t)
    }

    private fun firstIntegrityRow(connection: SQLiteConnection): String {
        val statement = connection.prepare(PRAGMA_INTEGRITY_CHECK)
        return try {
            if (statement.step()) statement.getText(0) else "integrity_check returned no result"
        } finally {
            statement.close()
        }
    }

    private const val PRAGMA_INTEGRITY_CHECK = "PRAGMA integrity_check"
    private const val INTEGRITY_OK = "ok"
    private const val MAX_REASON_LENGTH = 200

    /** First 16 bytes of every SQLite 3 database file. */
    private val SQLITE_MAGIC: ByteArray = "SQLite format 3\u0000".encodeToByteArray()
}
