package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.zip.Crc32
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.zip.CRC32 as JavaCRC32

/** Cross-checks the KMP CRC32 against `java.util.zip.CRC32`. JVM-only. */
class Crc32JvmTest : FunSpec({

    test("matches java.util.zip.CRC32 for empty input") {
        Crc32().getValue() shouldBe JavaCRC32().value
    }

    test("matches java.util.zip.CRC32 across diverse byte sequences") {
        val cases = listOf(
            byteArrayOf(0),
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9),
            "MoneySurfer".encodeToByteArray(),
            ByteArray(8192) { (it and 0xFF).toByte() },
        )
        for (bytes in cases) {
            val ours = Crc32().also { it.update(bytes) }.getValue()
            val java = JavaCRC32().also { it.update(bytes) }.value
            ours shouldBe java
        }
    }
})
