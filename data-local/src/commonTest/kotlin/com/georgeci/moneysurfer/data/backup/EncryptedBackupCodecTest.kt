package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.crypto.EncryptedBackupCodec
import com.georgeci.moneysurfer.domain.backup.BackupError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EncryptedBackupCodecTest : FunSpec({

    val plaintext = "the quick brown fox jumps over the lazy dog".encodeToByteArray()

    test("seal + open round-trips with the right passphrase") {
        val sealed = EncryptedBackupCodec.seal(plaintext, "correct horse battery staple")
        EncryptedBackupCodec.open(sealed, "correct horse battery staple") shouldBe plaintext
    }

    test("sealed blob does not contain the plaintext") {
        val sealed = EncryptedBackupCodec.seal(plaintext, "pass")
        // Search for the plaintext bytes anywhere in the sealed blob.
        val haystack = sealed.toList()
        val needle = plaintext.toList()
        val leaked = (0..haystack.size - needle.size).any { start ->
            haystack.subList(start, start + needle.size) == needle
        }
        leaked shouldBe false
    }

    test("two seals of the same plaintext differ (fresh salt + IV)") {
        val first = EncryptedBackupCodec.seal(plaintext, "pass")
        val second = EncryptedBackupCodec.seal(plaintext, "pass")
        first.contentEquals(second) shouldBe false
    }

    test("wrong passphrase is rejected before decryption") {
        val sealed = EncryptedBackupCodec.seal(plaintext, "right")
        shouldThrow<BackupError.WrongPassphrase> {
            EncryptedBackupCodec.open(sealed, "wrong")
        }
    }

    test("a single flipped ciphertext byte fails the MAC") {
        val sealed = EncryptedBackupCodec.seal(plaintext, "pass")
        val tamperIndex = sealed.size / 2
        sealed[tamperIndex] = (sealed[tamperIndex].toInt() xor 1).toByte()
        shouldThrow<BackupError.WrongPassphrase> {
            EncryptedBackupCodec.open(sealed, "pass")
        }
    }

    test("a truncated blob fails the MAC") {
        val sealed = EncryptedBackupCodec.seal(plaintext, "pass")
        shouldThrow<BackupError.WrongPassphrase> {
            EncryptedBackupCodec.open(sealed.copyOfRange(0, sealed.size - 1), "pass")
        }
    }

    test("garbage without the magic prefix is rejected as InvalidArchive") {
        shouldThrow<BackupError.InvalidArchive> {
            EncryptedBackupCodec.open(ByteArray(128) { it.toByte() }, "pass")
        }
    }

    test("blob shorter than header + MAC is rejected as InvalidArchive") {
        shouldThrow<BackupError.InvalidArchive> {
            EncryptedBackupCodec.open(EncryptedBackupCodec.MAGIC, "pass")
        }
    }

    test("empty plaintext round-trips") {
        val sealed = EncryptedBackupCodec.seal(ByteArray(0), "pass")
        EncryptedBackupCodec.open(sealed, "pass") shouldBe ByteArray(0)
    }

    test("unicode passphrases round-trip and are distinct from lookalikes") {
        val sealed = EncryptedBackupCodec.seal(plaintext, "пароль-🌊")
        EncryptedBackupCodec.open(sealed, "пароль-🌊") shouldBe plaintext
        shouldThrow<BackupError.WrongPassphrase> {
            EncryptedBackupCodec.open(sealed, "пароль-")
        }
    }

    test("magic constant is stable (backup format compatibility)") {
        EncryptedBackupCodec.MAGIC.decodeToString() shouldBe "MSBKENC1"
        EncryptedBackupCodec.PBKDF2_ITERATIONS shouldNotBe 0
    }
})
