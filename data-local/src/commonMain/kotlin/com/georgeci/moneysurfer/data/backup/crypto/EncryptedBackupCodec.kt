package com.georgeci.moneysurfer.data.backup.crypto

import com.georgeci.moneysurfer.domain.backup.BackupError

/**
 * Sealed-blob format behind `payload.enc` (scheme
 * [com.georgeci.moneysurfer.domain.backup.BackupEncryptionInfo.SCHEME_V1]):
 *
 * ```
 * magic      8 bytes  "MSBKENC1"
 * salt      16 bytes  PBKDF2 salt
 * iv        16 bytes  AES-CTR initial counter block
 * ciphertext N bytes  AES-256-CTR of the inner backup ZIP
 * mac       32 bytes  HMAC-SHA256 over magic ‖ salt ‖ iv ‖ ciphertext
 * ```
 *
 * Encrypt-then-MAC: [open] verifies the MAC over the full prefix before
 * touching the ciphertext, so a tampered or wrong-passphrase blob is rejected
 * without ever feeding attacker-controlled plaintext further down the import
 * pipeline. PBKDF2 derives 64 bytes — first half AES key, second half MAC key.
 */
internal object EncryptedBackupCodec {

    fun seal(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = BackupCrypto.randomBytes(SALT_SIZE)
        val iv = BackupCrypto.randomBytes(IV_SIZE)
        val keys = deriveKeys(passphrase, salt)
        val ciphertext = BackupCrypto.aesCtr(keys.aesKey, iv, plaintext)

        val macInput = MAGIC + salt + iv + ciphertext
        val mac = BackupCrypto.hmacSha256(keys.macKey, macInput)
        return macInput + mac
    }

    fun open(blob: ByteArray, passphrase: String): ByteArray {
        if (blob.size < HEADER_SIZE + MAC_SIZE || !blob.startsWith(MAGIC)) {
            throw BackupError.InvalidArchive("Encrypted payload has an unrecognised format")
        }
        val salt = blob.copyOfRange(MAGIC_SIZE, MAGIC_SIZE + SALT_SIZE)
        val iv = blob.copyOfRange(MAGIC_SIZE + SALT_SIZE, HEADER_SIZE)
        val keys = deriveKeys(passphrase, salt)

        val macInput = blob.copyOfRange(0, blob.size - MAC_SIZE)
        val mac = blob.copyOfRange(blob.size - MAC_SIZE, blob.size)
        val expectedMac = BackupCrypto.hmacSha256(keys.macKey, macInput)
        if (!constantTimeEquals(expectedMac, mac)) {
            throw BackupError.WrongPassphrase
        }

        val ciphertext = blob.copyOfRange(HEADER_SIZE, blob.size - MAC_SIZE)
        return BackupCrypto.aesCtr(keys.aesKey, iv, ciphertext)
    }

    private fun deriveKeys(passphrase: String, salt: ByteArray): DerivedKeys {
        val derived = BackupCrypto.pbkdf2Sha256(
            passphrase = passphrase,
            salt = salt,
            iterations = PBKDF2_ITERATIONS,
            keyLengthBytes = KEY_SIZE * 2,
        )
        return DerivedKeys(
            aesKey = derived.copyOfRange(0, KEY_SIZE),
            macKey = derived.copyOfRange(KEY_SIZE, KEY_SIZE * 2),
        )
    }

    private class DerivedKeys(val aesKey: ByteArray, val macKey: ByteArray)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }

    val MAGIC: ByteArray = "MSBKENC1".encodeToByteArray()

    private const val MAGIC_SIZE = 8
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 16
    private const val KEY_SIZE = 32
    private const val MAC_SIZE = 32
    private const val HEADER_SIZE = MAGIC_SIZE + SALT_SIZE + IV_SIZE

    /** OWASP-recommended order of magnitude for PBKDF2-HMAC-SHA256 on mobile. */
    const val PBKDF2_ITERATIONS: Int = 210_000
}
