package com.georgeci.moneysurfer.data.backup.zip

import com.georgeci.moneysurfer.domain.backup.BackupError
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource

/**
 * Streaming reader for ZIPs produced by [ZipStoredWriter].
 *
 * Reads local-file-headers in order and ignores the trailing central directory.
 * Only the STORED method is supported — a non-zero compression method is a
 * hard error.
 */
internal class ZipStoredReader(private val source: BufferedSource) {

    /** Reads the next entry's name. Returns `null` once the central directory is reached. */
    fun nextEntryName(): String? {
        if (source.exhausted()) return null
        val signature = source.readIntLe()
        if (signature != LOCAL_FILE_HEADER_SIGNATURE) {
            // Treat anything else (central directory, EOCD) as end of stream.
            return null
        }
        currentHeader = readLocalFileHeader()
        return currentHeader?.name
    }

    /** Declared size of the most-recent entry, or `null` outside an entry. */
    val currentEntrySize: Long?
        get() = currentHeader?.size?.toLong()

    /**
     * Streams the body of the most-recent entry into [out] and verifies the CRC32.
     * Throws [BackupError.CrcMismatch] on mismatch.
     */
    fun readCurrentEntryTo(out: BufferedSink) {
        val header = currentHeader ?: error("nextEntryName() returned null or was not called")
        val crc = Crc32()
        var remaining = header.size
        val readBuffer = ByteArray(COPY_BUFFER_SIZE)
        while (remaining > 0) {
            val toRead = minOf(remaining.toLong(), readBuffer.size.toLong()).toInt()
            val n = source.read(readBuffer, 0, toRead)
            if (n == -1) {
                throw BackupError.InvalidArchive("Truncated entry: ${header.name}")
            }
            crc.update(readBuffer, length = n)
            out.write(readBuffer, 0, n)
            remaining -= n
        }
        if (crc.getValue() != header.crc) {
            throw BackupError.CrcMismatch(header.name)
        }
        currentHeader = null
    }

    /** Reads the current entry into a fresh [Buffer]. */
    fun readCurrentEntryToBuffer(): Buffer {
        val buffer = Buffer()
        readCurrentEntryTo(buffer)
        return buffer
    }

    private var currentHeader: LocalEntry? = null

    private fun readLocalFileHeader(): LocalEntry {
        val versionNeeded = source.readShortLe().toInt() and MASK_U16
        val flags = source.readShortLe().toInt() and MASK_U16
        val method = source.readShortLe().toInt() and MASK_U16
        source.readShortLe()
        source.readShortLe()
        val crc = source.readIntLe().toLong() and MASK_U32
        val compressedSize = source.readIntLe().toLong() and MASK_U32
        val uncompressedSize = source.readIntLe().toLong() and MASK_U32
        val nameLength = source.readShortLe().toInt() and MASK_U16
        val extraLength = source.readShortLe().toInt() and MASK_U16

        val problem = when {
            method != COMPRESSION_METHOD_STORED ->
                "Unsupported compression method: $method"
            flags and GENERAL_PURPOSE_DATA_DESCRIPTOR_FLAG != 0 ->
                "Data-descriptor entries are not supported"
            versionNeeded > VERSION_NEEDED_TO_EXTRACT ->
                "ZIP requires version $versionNeeded"
            compressedSize != uncompressedSize ->
                "STORED entry has mismatched sizes"
            // The codec keeps each entry buffered as a single okio Segment chain,
            // so anything above Int.MAX_VALUE bytes can't be addressed in the
            // ByteArray copy loop and would silently overflow on the cast below.
            // We don't ship multi-GB DBs, so reject up front.
            uncompressedSize > Int.MAX_VALUE.toLong() ->
                "Entry too large: $uncompressedSize bytes"
            else -> null
        }
        if (problem != null) {
            throw BackupError.InvalidArchive(problem)
        }

        val nameBytes = source.readByteArray(nameLength.toLong())
        val name = nameBytes.decodeToString()
        if (extraLength > 0) source.skip(extraLength.toLong())
        return LocalEntry(name = name, size = uncompressedSize.toInt(), crc = crc)
    }

    private data class LocalEntry(val name: String, val size: Int, val crc: Long)
}

private const val GENERAL_PURPOSE_DATA_DESCRIPTOR_FLAG: Int = 1 shl 3
private const val COPY_BUFFER_SIZE: Int = 64 * 1024
private const val MASK_U16: Int = 0xFFFF
private const val MASK_U32: Long = 0xFFFFFFFFL
