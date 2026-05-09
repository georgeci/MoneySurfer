package com.georgeci.moneysurfer.data.backup.zip

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource

/**
 * Minimal ZIP encoder using only the STORED (no compression) method.
 *
 * SQLite databases and DataStore preference files do not compress meaningfully,
 * so STORED is the right trade-off — and it lets us keep the implementation
 * tiny and KMP-portable (no `java.util.zip` on iOS).
 *
 * Each entry is fully buffered in memory before its local-file-header is
 * written (so size + CRC are known up front and we avoid data-descriptors).
 * Files in this app are bounded — a personal-finance database is rarely above
 * tens of MB — so this is acceptable.
 */
internal class ZipStoredWriter(private val sink: BufferedSink) {

    private val entries: MutableList<CentralDirEntry> = mutableListOf()
    private var bytesWritten: Long = 0
    private var closed: Boolean = false

    fun writeEntry(name: String, data: BufferedSource) {
        check(!closed) { "ZIP archive already finished" }

        val payload = Buffer()
        val crc = Crc32()
        val readBuffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val n = data.read(readBuffer)
            if (n == -1) break
            crc.update(readBuffer, length = n)
            payload.write(readBuffer, 0, n)
        }

        val nameBytes = name.encodeToByteArray()
        val crcValue = crc.getValue()
        val size = payload.size

        val localHeaderOffset = bytesWritten
        bytesWritten += writeLocalFileHeader(nameBytes, crcValue, size)
        sink.writeAll(payload)
        bytesWritten += size

        entries += CentralDirEntry(
            nameBytes = nameBytes,
            crc = crcValue,
            size = size,
            localHeaderOffset = localHeaderOffset,
        )
    }

    fun finish() {
        check(!closed) { "ZIP archive already finished" }
        closed = true

        val centralDirOffset = bytesWritten
        var centralDirSize = 0L
        for (entry in entries) {
            centralDirSize += writeCentralDirHeader(entry)
        }
        bytesWritten += centralDirSize
        writeEndOfCentralDir(centralDirOffset, centralDirSize)
        sink.flush()
    }

    private fun writeLocalFileHeader(nameBytes: ByteArray, crc: Long, size: Long): Long {
        sink.writeIntLe(LOCAL_FILE_HEADER_SIGNATURE)
        sink.writeShortLe(VERSION_NEEDED_TO_EXTRACT)
        sink.writeShortLe(GENERAL_PURPOSE_BIT_FLAG)
        sink.writeShortLe(COMPRESSION_METHOD_STORED)
        sink.writeShortLe(MS_DOS_TIME)
        sink.writeShortLe(MS_DOS_DATE)
        sink.writeIntLe(crc.toInt())
        sink.writeIntLe(size.toInt())
        sink.writeIntLe(size.toInt())
        sink.writeShortLe(nameBytes.size)
        sink.writeShortLe(0)
        sink.write(nameBytes)
        return LOCAL_FILE_HEADER_FIXED_SIZE + nameBytes.size.toLong()
    }

    private fun writeCentralDirHeader(entry: CentralDirEntry): Long {
        sink.writeIntLe(CENTRAL_DIR_HEADER_SIGNATURE)
        sink.writeShortLe(VERSION_MADE_BY)
        sink.writeShortLe(VERSION_NEEDED_TO_EXTRACT)
        sink.writeShortLe(GENERAL_PURPOSE_BIT_FLAG)
        sink.writeShortLe(COMPRESSION_METHOD_STORED)
        sink.writeShortLe(MS_DOS_TIME)
        sink.writeShortLe(MS_DOS_DATE)
        sink.writeIntLe(entry.crc.toInt())
        sink.writeIntLe(entry.size.toInt())
        sink.writeIntLe(entry.size.toInt())
        sink.writeShortLe(entry.nameBytes.size)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeIntLe(0)
        sink.writeIntLe(entry.localHeaderOffset.toInt())
        sink.write(entry.nameBytes)
        return CENTRAL_DIR_HEADER_FIXED_SIZE + entry.nameBytes.size.toLong()
    }

    private fun writeEndOfCentralDir(centralDirOffset: Long, centralDirSize: Long) {
        sink.writeIntLe(END_OF_CENTRAL_DIR_SIGNATURE)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(entries.size)
        sink.writeShortLe(entries.size)
        sink.writeIntLe(centralDirSize.toInt())
        sink.writeIntLe(centralDirOffset.toInt())
        sink.writeShortLe(0)
    }

    private data class CentralDirEntry(
        val nameBytes: ByteArray,
        val crc: Long,
        val size: Long,
        val localHeaderOffset: Long,
    )
}

@Suppress("MagicNumber")
internal const val LOCAL_FILE_HEADER_SIGNATURE: Int = 0x04034b50

@Suppress("MagicNumber")
internal const val CENTRAL_DIR_HEADER_SIGNATURE: Int = 0x02014b50

@Suppress("MagicNumber")
internal const val END_OF_CENTRAL_DIR_SIGNATURE: Int = 0x06054b50

internal const val VERSION_NEEDED_TO_EXTRACT: Int = 20
internal const val VERSION_MADE_BY: Int = 20
internal const val GENERAL_PURPOSE_BIT_FLAG: Int = 0
internal const val COMPRESSION_METHOD_STORED: Int = 0
internal const val MS_DOS_TIME: Int = 0
internal const val MS_DOS_DATE: Int = 0

internal const val LOCAL_FILE_HEADER_FIXED_SIZE: Long = 30
internal const val CENTRAL_DIR_HEADER_FIXED_SIZE: Long = 46

private const val COPY_BUFFER_SIZE: Int = 64 * 1024
