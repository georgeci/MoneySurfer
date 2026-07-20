package com.georgeci.moneysurfer.data.backup.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCryptorCreateWithMode
import platform.CoreCrypto.CCCryptorRefVar
import platform.CoreCrypto.CCCryptorRelease
import platform.CoreCrypto.CCCryptorUpdate
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.ccNoPadding
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCModeCTR
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.CoreCrypto.kCCSuccess
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.size_tVar

@OptIn(ExperimentalForeignApi::class)
internal actual object BackupCrypto {

    actual fun randomBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        if (count == 0) return bytes
        val status = bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, count.convert(), pinned.addressOf(0))
        }
        check(status == errSecSuccess) { "SecRandomCopyBytes failed: $status" }
        return bytes
    }

    actual fun pbkdf2Sha256(
        passphrase: String,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int,
    ): ByteArray {
        require(passphrase.isNotEmpty()) { "Passphrase must not be empty" }
        require(salt.isNotEmpty()) { "Salt must not be empty" }
        // The binding takes the password as a Kotlin String and encodes it as
        // UTF-8 — matching the char[]-as-UTF-8 behaviour of the JCA actual.
        val passphraseLength = passphrase.encodeToByteArray().size
        val derived = ByteArray(keyLengthBytes)
        val status = derived.usePinned { derivedPinned ->
            salt.usePinned { saltPinned ->
                CCKeyDerivationPBKDF(
                    kCCPBKDF2,
                    passphrase,
                    passphraseLength.convert(),
                    saltPinned.addressOf(0).reinterpret(),
                    salt.size.convert(),
                    kCCPRFHmacAlgSHA256,
                    iterations.convert(),
                    derivedPinned.addressOf(0).reinterpret(),
                    derived.size.convert(),
                )
            }
        }
        check(status == kCCSuccess) { "CCKeyDerivationPBKDF failed: $status" }
        return derived
    }

    actual fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        if (data.isEmpty()) return ByteArray(0)
        val out = ByteArray(data.size)
        memScoped {
            val cryptorRef = alloc<CCCryptorRefVar>()
            val createStatus = key.usePinned { keyPinned ->
                iv.usePinned { ivPinned ->
                    CCCryptorCreateWithMode(
                        kCCEncrypt, // CTR is symmetric; one direction covers both.
                        kCCModeCTR,
                        kCCAlgorithmAES,
                        ccNoPadding,
                        ivPinned.addressOf(0),
                        keyPinned.addressOf(0),
                        key.size.convert(),
                        null,
                        0.convert(),
                        0,
                        0.convert(),
                        cryptorRef.ptr,
                    )
                }
            }
            check(createStatus == kCCSuccess) { "CCCryptorCreateWithMode failed: $createStatus" }
            val cryptor = cryptorRef.value
            try {
                val moved = alloc<size_tVar>()
                val updateStatus = data.usePinned { dataPinned ->
                    out.usePinned { outPinned ->
                        CCCryptorUpdate(
                            cryptor,
                            dataPinned.addressOf(0),
                            data.size.convert(),
                            outPinned.addressOf(0),
                            out.size.convert(),
                            moved.ptr,
                        )
                    }
                }
                check(updateStatus == kCCSuccess && moved.value.toLong() == data.size.toLong()) {
                    "CCCryptorUpdate failed: $updateStatus"
                }
            } finally {
                CCCryptorRelease(cryptor)
            }
        }
        return out
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "HMAC key must not be empty" }
        require(data.isNotEmpty()) { "HMAC input must not be empty" }
        val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
        out.usePinned { outPinned ->
            key.usePinned { keyPinned ->
                data.usePinned { dataPinned ->
                    CCHmac(
                        kCCHmacAlgSHA256,
                        keyPinned.addressOf(0),
                        key.size.convert(),
                        dataPinned.addressOf(0),
                        data.size.convert(),
                        outPinned.addressOf(0),
                    )
                }
            }
        }
        return out
    }
}
