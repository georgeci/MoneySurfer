package com.georgeci.moneysurfer.data.backup.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal actual object BackupCrypto {

    private val secureRandom = SecureRandom()

    actual fun randomBytes(count: Int): ByteArray =
        ByteArray(count).also(secureRandom::nextBytes)

    actual fun pbkdf2Sha256(
        passphrase: String,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int,
    ): ByteArray {
        // JCA's PBKDF2WithHmacSHA256 encodes the char[] as UTF-8 — matching
        // the byte-oriented CommonCrypto actual on iOS.
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, keyLengthBytes * Byte.SIZE_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    actual fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        // CTR is symmetric; ENCRYPT_MODE serves both directions.
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
