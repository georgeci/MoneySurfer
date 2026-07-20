package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.fixtures.BackupTestHarness
import com.georgeci.moneysurfer.data.backup.fixtures.openDatabaseAt
import com.georgeci.moneysurfer.data.backup.fixtures.testAppInfo
import com.georgeci.moneysurfer.data.backup.fixtures.testClock
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.domain.backup.BackupEncryptionInfo
import com.georgeci.moneysurfer.domain.backup.BackupError
import com.georgeci.moneysurfer.domain.backup.BackupManifest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import okio.Buffer
import okio.FileSystem

/**
 * Passphrase-protected export/import path: outer archive shape, detection via
 * [BackupImporterImpl.isPassphraseProtected], and the passphrase error modes.
 */
class BackupEncryptedRoundTripJvmTest : FunSpec({

    lateinit var harness: BackupTestHarness

    beforeEach { harness = BackupTestHarness() }
    afterEach { harness.close() }

    val passphrase = "correct horse battery staple"

    suspend fun exportEncrypted(): Pair<Buffer, BackupManifest> {
        harness.database().userDao().insert(
            UserEntity(id = "u-original", displayName = "Alice", isAnon = false),
        )
        FileSystem.SYSTEM.write(harness.locator.dataStoreFile()) { writeUtf8("PREFS:secret") }
        val backup = Buffer()
        val manifest = BackupExporterImpl(harness.database(), harness.locator, testAppInfo, testClock)
            .exportTo(backup, passphrase)
            .getOrThrow()
        return backup to manifest
    }

    test("encrypted export advertises the scheme and hides the data entries") {
        val (backup, manifest) = exportEncrypted()

        manifest.encryption shouldBe BackupEncryptionInfo(BackupEncryptionInfo.SCHEME_V1)
        manifest.files shouldContainExactly listOf(BackupManifest.ENCRYPTED_PAYLOAD_ENTRY_NAME)

        // The raw DB bytes must not appear in the archive (STORED zip = no
        // compression, so plaintext would be directly visible).
        val archiveBytes = backup.copy().readByteArray().toList()
        val secret = "PREFS:secret".encodeToByteArray().toList()
        val leaked = (0..archiveBytes.size - secret.size).any { start ->
            archiveBytes.subList(start, start + secret.size) == secret
        }
        leaked shouldBe false
    }

    test("encrypted round-trip restores data with the right passphrase") {
        val (backup, _) = exportEncrypted()
        val locator = harness.locator

        harness.database().close()
        FileSystem.SYSTEM.deleteIfExists(locator.moneySurferDbFile())
        FileSystem.SYSTEM.deleteIfExists(locator.dataStoreFile())

        val importTargetDb = openDatabaseAt(locator.moneySurferDbFile())
        val importer = BackupImporterImpl(importTargetDb, locator)

        // Detection first — must not consume the source.
        importer.isPassphraseProtected(backup) shouldBe true
        importer.importFrom(backup, passphrase).getOrThrow()

        FileSystem.SYSTEM.read(locator.dataStoreFile()) { readUtf8() } shouldBe "PREFS:secret"
        val verifyDb = openDatabaseAt(locator.moneySurferDbFile())
        try {
            verifyDb.userDao().getAll().first().map { it.id } shouldContainExactly listOf("u-original")
        } finally {
            verifyDb.close()
        }
    }

    test("encrypted import without a passphrase surfaces PassphraseRequired") {
        val (backup, _) = exportEncrypted()
        val importer = BackupImporterImpl(harness.database(), harness.locator)
        shouldThrow<BackupError.PassphraseRequired> {
            importer.importFrom(backup).getOrThrow()
        }
    }

    test("encrypted import with the wrong passphrase surfaces WrongPassphrase") {
        val (backup, _) = exportEncrypted()
        val importer = BackupImporterImpl(harness.database(), harness.locator)
        shouldThrow<BackupError.WrongPassphrase> {
            importer.importFrom(backup, "not-the-passphrase").getOrThrow()
        }
    }

    test("tampering with the sealed payload surfaces WrongPassphrase") {
        val (backup, _) = exportEncrypted()
        val bytes = backup.readByteArray()
        // Flip a byte inside the sealed payload body (past the outer manifest);
        // also fix nothing else — the entry CRC is over the sealed bytes, so
        // flip both a payload byte and expect either CRC or MAC rejection.
        val flipIndex = bytes.size / 2
        bytes[flipIndex] = (bytes[flipIndex].toInt() xor 1).toByte()
        val importer = BackupImporterImpl(harness.database(), harness.locator)
        val thrown = shouldThrow<BackupError> {
            importer.importFrom(Buffer().apply { write(bytes) }, passphrase).getOrThrow()
        }
        (thrown is BackupError.WrongPassphrase || thrown is BackupError.CrcMismatch) shouldBe true
    }

    test("plain backups report isPassphraseProtected = false and import unchanged") {
        harness.database().userDao().insert(
            UserEntity(id = "u-plain", displayName = "Bob", isAnon = false),
        )
        FileSystem.SYSTEM.write(harness.locator.dataStoreFile()) { writeUtf8("PREFS:plain") }
        val backup = Buffer()
        BackupExporterImpl(harness.database(), harness.locator, testAppInfo, testClock)
            .exportTo(backup)
            .getOrThrow()

        val importer = BackupImporterImpl(harness.database(), harness.locator)
        importer.isPassphraseProtected(backup) shouldBe false
        importer.importFrom(backup).getOrThrow()
    }
})
