package com.georgeci.moneysurfer.data.backup.zip

/**
 * IEEE 802.3 CRC-32 — same polynomial as ZIP / PNG / `java.util.zip.CRC32`.
 *
 * Table-based, KMP-native (no java.util.zip on iOS).
 */
internal class Crc32 {
    private var value: Int = 0.inv()

    fun update(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset) {
        var crc = value
        val end = offset + length
        var i = offset
        while (i < end) {
            crc = (crc ushr 8) xor TABLE[(crc xor buffer[i].toInt()) and 0xFF]
            i++
        }
        value = crc
    }

    fun getValue(): Long = (value.inv().toLong() and 0xFFFFFFFFL)

    companion object {
        private val TABLE: IntArray = IntArray(TABLE_SIZE).also { table ->
            for (n in 0 until TABLE_SIZE) {
                var c = n
                repeat(BITS_PER_BYTE) {
                    c = if (c and 1 != 0) (c ushr 1) xor POLY else c ushr 1
                }
                table[n] = c
            }
        }
    }
}

private const val TABLE_SIZE = 256
private const val BITS_PER_BYTE = 8

@Suppress("MagicNumber")
private const val POLY: Int = 0xEDB88320.toInt()
