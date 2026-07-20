package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.fixtures.BackupTestHarness
import com.georgeci.moneysurfer.data.backup.fixtures.buildArchive
import com.georgeci.moneysurfer.data.backup.fixtures.manifestJson
import com.georgeci.moneysurfer.data.backup.fixtures.validSqliteDbBytes
import com.georgeci.moneysurfer.data.backup.zip.ZipStoredWriter
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.domain.backup.BackupError
import com.georgeci.moneysurfer.domain.backup.BackupManifest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import okio.Buffer
import okio.FileSystem

/**
 * Importer error-path coverage: every [BackupError] subtype is reachable via
 * a hand-crafted ZIP. Early-rejection cases never reach the file swap, so
 * the Room handle is left intact and reused across cases.
 */
class BackupImporterImplJvmTest : FunSpec({

    lateinit var harness: BackupTestHarness
    lateinit var importer: BackupImporterImpl

    beforeEach {
        harness = BackupTestHarness()
        importer = BackupImporterImpl(harness.database(), harness.locator)
    }
    afterEach { harness.close() }

    test("empty archive surfaces InvalidArchive") {
        shouldThrow<BackupError.InvalidArchive> {
            importer.importFrom(Buffer()).getOrThrow()
        }
    }

    test("missing manifest entry surfaces InvalidArchive") {
        val sink = Buffer()
        ZipStoredWriter(sink).apply {
            writeEntry(BackupManifest.MAIN_DB_ENTRY_NAME, Buffer().apply { writeUtf8("data") })
            finish()
        }
        shouldThrow<BackupError.InvalidArchive> {
            importer.importFrom(sink).getOrThrow()
        }
    }

    test("non-JSON manifest surfaces InvalidArchive") {
        val sink = Buffer()
        ZipStoredWriter(sink).apply {
            writeEntry(BackupManifest.MANIFEST_ENTRY_NAME, Buffer().apply { writeUtf8("not-json") })
            writeEntry(BackupManifest.MAIN_DB_ENTRY_NAME, Buffer().apply { writeUtf8("x") })
            writeEntry(BackupManifest.DATASTORE_ENTRY_NAME, Buffer().apply { writeUtf8("y") })
            finish()
        }
        shouldThrow<BackupError.InvalidArchive> {
            importer.importFrom(sink).getOrThrow()
        }
    }

    test("future format version surfaces FormatMismatch") {
        val archive = buildArchive(manifestJson(backupFormatVersion = 99))
        val error = shouldThrow<BackupError.FormatMismatch> {
            importer.importFrom(archive).getOrThrow()
        }
        error.expected shouldBe BackupManifest.CURRENT_FORMAT_VERSION
        error.actual shouldBe 99
    }

    test("schema-version mismatch surfaces SchemaMismatch") {
        val archive = buildArchive(manifestJson(moneySurferDbVersion = 999))
        val error = shouldThrow<BackupError.SchemaMismatch> {
            importer.importFrom(archive).getOrThrow()
        }
        error.actual shouldBe 999
    }

    test("manifest declaring a missing file surfaces MissingFile") {
        val archive = buildArchive(
            manifestJson(files = listOf(BackupManifest.MAIN_DB_ENTRY_NAME)),
        )
        val error = shouldThrow<BackupError.MissingFile> {
            importer.importFrom(archive).getOrThrow()
        }
        error.name shouldBe BackupManifest.DATASTORE_ENTRY_NAME
    }

    test("archive missing the main DB entry surfaces MissingFile") {
        val archive = buildArchive(manifestJson(), includeMainDb = false)
        val error = shouldThrow<BackupError.MissingFile> {
            importer.importFrom(archive).getOrThrow()
        }
        error.name shouldBe BackupManifest.MAIN_DB_ENTRY_NAME
    }

    test("archive missing the DataStore entry surfaces MissingFile") {
        val archive = buildArchive(manifestJson(), includeDataStore = false)
        val error = shouldThrow<BackupError.MissingFile> {
            importer.importFrom(archive).getOrThrow()
        }
        error.name shouldBe BackupManifest.DATASTORE_ENTRY_NAME
    }

    test("CRC corruption is rejected") {
        val archive = buildArchive(manifestJson())
        val bytes = archive.readByteArray()
        // Flip a byte deep inside the archive so it lands in an entry body
        // (not a header). The exact offset isn't critical — any mid-body
        // corruption should produce CrcMismatch; a header flip would yield
        // InvalidArchive instead. Both are correct rejections.
        val flipIndex = bytes.size / 2
        bytes[flipIndex] = (bytes[flipIndex] + 1).toByte()

        val corrupted = Buffer().apply { write(bytes) }
        val thrown = shouldThrow<BackupError> {
            importer.importFrom(corrupted).getOrThrow()
        }
        (thrown is BackupError.CrcMismatch || thrown is BackupError.InvalidArchive) shouldBe true
    }

    test("unknown extra entry between manifest and main DB is tolerated") {
        // Forward-compat slack: a future version may add entries; the importer
        // ignores unknown names rather than rejecting the whole archive.
        val sink = Buffer()
        ZipStoredWriter(sink).apply {
            writeEntry(BackupManifest.MANIFEST_ENTRY_NAME, Buffer().apply { writeUtf8(manifestJson()) })
            writeEntry("future/unknown.bin", Buffer().apply { writeUtf8("ignored") })
            writeEntry(BackupManifest.MAIN_DB_ENTRY_NAME, Buffer().apply { write(validSqliteDbBytes()) })
            writeEntry(BackupManifest.DATASTORE_ENTRY_NAME, Buffer().apply { writeUtf8("ds") })
            finish()
        }
        importer.importFrom(sink).getOrThrow().shouldBeInstanceOf<BackupManifest>()
    }

    test("main DB payload without the SQLite header surfaces CorruptDatabase") {
        val archive = buildArchive(manifestJson(), mainDbBytes = ByteArray(64) { 7 })
        val error = shouldThrow<BackupError.CorruptDatabase> {
            importer.importFrom(archive).getOrThrow()
        }
        error.reason shouldContain "not an SQLite database"
    }

    test("SQLite header with a corrupted structure fails the integrity check") {
        // Keep the 16-byte magic intact but break the page-size field (bytes
        // 16-17, big-endian; must be a power of two) so SQLite rejects the file.
        val corrupted = validSqliteDbBytes().also {
            it[16] = 0x00
            it[17] = 0x03
        }
        val archive = buildArchive(manifestJson(), mainDbBytes = corrupted)
        shouldThrow<BackupError.CorruptDatabase> {
            importer.importFrom(archive).getOrThrow()
        }
    }

    test("rejected payload never replaces the live DB and leaves no temp files") {
        val fs = FileSystem.SYSTEM
        val mainDbPath = harness.locator.moneySurferDbFile()
        // Room creates the file lazily — force it into existence first.
        harness.database().userDao().insert(
            UserEntity(id = "u-live", displayName = "Live", isAnon = false),
        )
        val liveBytes = fs.read(mainDbPath) { readByteArray() }

        val archive = buildArchive(manifestJson(), mainDbBytes = ByteArray(64) { 7 })
        shouldThrow<BackupError.CorruptDatabase> {
            importer.importFrom(archive).getOrThrow()
        }

        fs.read(mainDbPath) { readByteArray() } shouldBe liveBytes
        val leftovers = fs.list(harness.tempDir).filter { ".restore.tmp" in it.name }
        leftovers shouldBe emptyList()
    }

    test("oversized manifest entry is rejected before parsing") {
        val hugeManifest = "x".repeat(2 * 1024 * 1024)
        val sink = Buffer()
        ZipStoredWriter(sink).apply {
            writeEntry(BackupManifest.MANIFEST_ENTRY_NAME, Buffer().apply { writeUtf8(hugeManifest) })
            finish()
        }
        val error = shouldThrow<BackupError.InvalidArchive> {
            importer.importFrom(sink).getOrThrow()
        }
        error.reason shouldContain "too large"
    }
})
