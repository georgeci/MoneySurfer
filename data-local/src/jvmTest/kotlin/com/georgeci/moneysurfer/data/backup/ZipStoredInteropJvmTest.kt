package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.zip.ZipStoredWriter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Confirms our ZIP is readable by standard `java.util.zip` tooling. */
class ZipStoredInteropJvmTest : FunSpec({

    test("java.util.zip.ZipInputStream reads our stored archive") {
        val sink = Buffer()
        val writer = ZipStoredWriter(sink)
        writer.writeEntry("first.txt", Buffer().apply { writeUtf8("alpha") })
        writer.writeEntry("nested/second.bin", Buffer().apply { write(byteArrayOf(10, 20, 30)) })
        writer.finish()

        val zipBytes = sink.readByteArray()
        val zis = ZipInputStream(ByteArrayInputStream(zipBytes))

        val entry1 = zis.nextEntry
        entry1?.name shouldBe "first.txt"
        zis.readBytes().decodeToString() shouldBe "alpha"

        val entry2 = zis.nextEntry
        entry2?.name shouldBe "nested/second.bin"
        zis.readBytes() shouldBe byteArrayOf(10, 20, 30)

        zis.nextEntry shouldBe null
        zis.close()
    }
})
