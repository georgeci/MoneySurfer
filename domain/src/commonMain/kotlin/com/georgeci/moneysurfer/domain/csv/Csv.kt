package com.georgeci.moneysurfer.domain.csv

/**
 * One parsed CSV record. [rowNumber] is the 1-based record index in the file
 * (the header is row 1), counting logical records — a quoted field with an
 * embedded newline still advances the row number by one.
 */
data class CsvRecord(val rowNumber: Int, val fields: List<String>)

/**
 * Minimal RFC 4180-style CSV codec. Fields containing commas, quotes, or
 * newlines are quoted on write; quotes are doubled. The parser accepts LF and
 * CRLF line endings, a leading UTF-8 BOM, and skips fully blank records.
 */
internal object Csv {

    fun encodeRecord(fields: List<String>): String =
        fields.joinToString(separator = ",", transform = ::encodeField)

    private val charsRequiringQuotes = charArrayOf(',', '"', '\n', '\r')

    // Leading characters a spreadsheet reads as the start of a formula (or a
    // DDE payload). A cell beginning with one of these is evaluated when the
    // file is opened in Excel/LibreOffice — the CSV injection vector.
    private val formulaTriggers = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Neutralises spreadsheet formula injection in free-text fields. A field
     * whose first character would start a formula gets an apostrophe prefix,
     * which forces spreadsheets to treat the whole cell as literal text. The
     * apostrophe is itself escaped (a leading `'` is doubled) so the transform
     * is reversible: [unguardFormula] strips exactly one leading apostrophe and
     * recovers the original value losslessly. Applied only to attacker-supplied
     * free text — structured columns (ids, amounts, dates) stay untouched so
     * they still import into a spreadsheet as their proper types.
     */
    fun guardFormula(field: String): String {
        val first = field.firstOrNull() ?: return field
        return if (first == '\'' || first in formulaTriggers) "'$field" else field
    }

    /** Reverses [guardFormula]: drops one leading apostrophe if present. */
    fun unguardFormula(field: String): String =
        if (field.startsWith('\'')) field.substring(1) else field

    private fun encodeField(field: String): String =
        if (field.any { it in charsRequiringQuotes }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    fun parseRecords(text: String): List<CsvRecord> =
        RecordParser(text.removePrefix("\uFEFF")).parse()

    private class RecordParser(private val text: String) {
        private val records = mutableListOf<CsvRecord>()
        private val fields = mutableListOf<String>()
        private val field = StringBuilder()
        private var rowNumber = 1
        private var index = 0

        fun parse(): List<CsvRecord> {
            while (index < text.length) {
                if (inQuotes()) consumeQuoted() else consumePlain()
            }
            if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
            return records
        }

        private var quoted = false
        private fun inQuotes(): Boolean = quoted

        private fun consumeQuoted() {
            val char = text[index]
            when {
                char != '"' -> {
                    field.append(char)
                    index++
                }
                index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index += 2
                }
                else -> {
                    quoted = false
                    index++
                }
            }
        }

        private fun consumePlain() {
            when (val char = text[index]) {
                '"' -> quoted = true
                ',' -> endField()
                '\n' -> endRecord()
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') index++
                    endRecord()
                }
                else -> field.append(char)
            }
            index++
        }

        private fun endField() {
            fields.add(field.toString())
            field.clear()
        }

        private fun endRecord() {
            endField()
            // A record whose only field is empty is a blank line, not data.
            if (fields.size > 1 || fields.single().isNotEmpty()) {
                records.add(CsvRecord(rowNumber, fields.toList()))
            }
            fields.clear()
            rowNumber++
        }
    }
}
