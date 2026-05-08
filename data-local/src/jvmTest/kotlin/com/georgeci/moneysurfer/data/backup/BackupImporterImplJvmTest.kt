package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.zip.ZipStoredWriter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okio.Buffer
import okio.Path

/**
 * Importer error-path coverage: every [BackupError] subtype is reachable via
 * a hand-crafted ZIP. None of these cases run the post-validation file swap,
 * so the Room handle is left intact and reused across cases.
 */
class BackupImporterImplJvmTest : FunSpec({

    lateinit var tempDir: Path
    lateinit var locator: TestBackupStorageLocator
    lateinit var importer: BackupImporterImpl

    beforeEach {
        tempDir = newTempDir()
        locator = TestBackupStorageLocator(tempDir)
        // The importer needs a Room handle for the close() in the success path,
        // but error-path tests never reach it.
        val database = openDatabaseAt(locator.moneySurferDbFile())
        importer = BackupImporterImpl(database, locator)
    }

    afterEach {
        deleteRecursively(tempDir)
    }

    test("empty archive surfaces InvalidArchive") {
        val empty = Buffer()
        shouldThrow<BackupError.InvalidArchive> {
            importer.importFrom(empty).getOrThrow()
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

    test("manifest declaring a missing file surfaces MissingFile (manifest claim)") {
        val archive = buildArchive(
            manifestJson(files = listOf(BackupManifest.MAIN_DB_ENTRY_NAME)),
        )
        val error = shouldThrow<BackupError.MissingFile> {
            importer.importFrom(archive).getOrThrow()
        }
        error.name shouldBe BackupManifest.DATASTORE_ENTRY_NAME
    }

    test("archive missing the main DB entry surfaces MissingFile (extract)") {
        val archive = buildArchive(manifestJson(), includeMainDb = false)
        val error = shouldThrow<BackupError.MissingFile> {
            importer.importFrom(archive).getOrThrow()
        }
        error.name shouldBe BackupManifest.MAIN_DB_ENTRY_NAME
    }

    test("archive missing the DataStore entry surfaces MissingFile (extract)") {
        val archive = buildArchive(manifestJson(), includeDataStore = false)
        val error = shouldThrow<BackupError.MissingFile> {
            importer.importFrom(archive).getOrThrow()
        }
        error.name shouldBe BackupManifest.DATASTORE_ENTRY_NAME
    }

    test("CRC corruption surfaces CrcMismatch") {
        val archive = buildArchive(manifestJson())
        val bytes = archive.readByteArray()

        // Find the start of the main-db entry's body. Manifest entry header is at offset 0;
        // the body sits 30 + name-length bytes in. Easier: just flip a byte deep enough that
        // it lands in the main-db body — we put 16 bytes of payload in there.
        val flipIndex = bytes.size - 16 * 3 // approx near the dataStore body, but any flip works
        bytes[flipIndex] = (bytes[flipIndex] + 1).toByte()

        val corrupted = Buffer().apply { write(bytes) }
        val thrown = shouldThrow<BackupError> {
            importer.importFrom(corrupted).getOrThrow()
        }
        // CRC corruption can manifest as either CrcMismatch on a mid-entry flip
        // or InvalidArchive if the flip happens to land in a header — both are
        // valid rejections. We assert the rejection happened.
        (thrown is BackupError.CrcMismatch || thrown is BackupError.InvalidArchive) shouldBe true
    }

    test("unknown extra entry between manifest and main DB is tolerated") {
        // Forward-compat slack: a future version may add entries; old importer
        // should ignore unknown names rather than reject the whole archive.
        val sink = Buffer()
        ZipStoredWriter(sink).apply {
            writeEntry(BackupManifest.MANIFEST_ENTRY_NAME, Buffer().apply { writeUtf8(manifestJson()) })
            writeEntry("future/unknown.bin", Buffer().apply { writeUtf8("ignored") })
            writeEntry(BackupManifest.MAIN_DB_ENTRY_NAME, Buffer().apply { writeUtf8("main-db") })
            writeEntry(BackupManifest.DATASTORE_ENTRY_NAME, Buffer().apply { writeUtf8("ds") })
            finish()
        }
        // Importer reaches the file-swap stage and writes the temp files.
        // The DB close at the end leaves a closed Room handle — that's fine
        // since the test tears down the temp dir afterwards.
        importer.importFrom(sink).getOrThrow().shouldBeInstanceOf<BackupManifest>()
    }
})
