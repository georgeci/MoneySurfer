package com.georgeci.moneysurfer.data.backup.crypto

/**
 * Thin platform shim over the crypto primitives the backup codec needs.
 * JVM/Android back it with `javax.crypto`; iOS with CommonCrypto. Kept
 * primitive-shaped on purpose — the format logic lives once, in
 * [EncryptedBackupCodec].
 */
internal expect object BackupCrypto {

    /** Cryptographically secure random bytes (salts, IVs). */
    fun randomBytes(count: Int): ByteArray

    /** PBKDF2 with HMAC-SHA256 over the UTF-8 bytes of [passphrase]. */
    fun pbkdf2Sha256(
        passphrase: String,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int,
    ): ByteArray

    /** AES-256 in CTR mode; the same operation encrypts and decrypts. */
    fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
}
