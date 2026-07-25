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

    // A leading apostrophe is a guard only when it precedes a doubled literal
    // apostrophe or a formula trigger — the two shapes [guardFormula] emits.
    private val guardedSecondChars = formulaTriggers + '\''

    /**
     * Neutralises spreadsheet formula injection in free-text fields. A field
     * whose first character would start a formula gets an apostrophe prefix,
     * which forces spreadsheets to treat the whole cell as literal text. The
     * apostrophe is itself escaped (a leading `'` is doubled) so the transform
     * is reversible: [unguardFormula] recovers the original value for anything
     * this codec encoded. Applied only to attacker-supplied free text —
     * structured columns (ids, amounts, dates) stay untouched so they still
     * import into a spreadsheet as their proper types.
     */
    fun guardFormula(field: String): String {
        val first = field.firstOrNull() ?: return field
        return if (first == '\'' || first in formulaTriggers) "'$field" else field
    }

    /**
     * Reverses [guardFormula]. Strips a leading apostrophe only when it is one
     * this codec could have emitted — an apostrophe doubling an escaped literal
     * `'`, or an apostrophe guarding a formula trigger. A note that merely
     * starts with an apostrophe followed by ordinary text (a legacy pre-guard
     * export, or a third-party CSV) is left untouched, so import stays lossless
     * for those too.
     */
    fun unguardFormula(field: String): String =
        if (field.length >= 2 && field[0] == '\'' && field[1] in guardedSecondChars) {
            field.substring(1)
        } else {
            field
        }

    /**
     * Separator for a list packed into a single CSV field. Deliberately not the
     * `,` the Room column uses — that is this file's field delimiter, so a
     * comma-joined list would either split into columns or force quoting on
     * every non-empty cell. A pipe stays legible and editable in a spreadsheet,
     * which is the point of exporting the list at all.
     */
    private const val LIST_SEPARATOR = '|'

    /** Escape for a [LIST_SEPARATOR] (or an escape) that occurs inside a value. */
    private const val LIST_ESCAPE = '\\'

    /**
     * Packs [values] into one CSV field, escaping any separator inside a value
     * so the packing is lossless. Unlike the Room column — which strips its
     * separator from the value up front — nothing here constrains what a value
     * may contain, so a tag holding a literal `|` survives the round-trip
     * instead of splitting in two.
     */
    fun encodeCellList(values: List<String>): String =
        values.joinToString(LIST_SEPARATOR.toString(), transform = ::escapeListValue)

    /**
     * Reverses [encodeCellList]. An empty cell is an empty list; blank segments
     * (a trailing separator, a doubled one) come back as blank values for the
     * caller to drop. A dangling trailing escape is kept as a literal character
     * rather than swallowed — a hand-edited cell should not lose data.
     */
    fun decodeCellList(cell: String): List<String> {
        if (cell.isEmpty()) return emptyList()
        val values = mutableListOf<String>()
        val value = StringBuilder()
        var index = 0
        while (index < cell.length) {
            val char = cell[index]
            when {
                char == LIST_ESCAPE && index + 1 < cell.length -> {
                    value.append(cell[index + 1])
                    index += 2
                }
                char == LIST_SEPARATOR -> {
                    values.add(value.toString())
                    value.clear()
                    index++
                }
                else -> {
                    value.append(char)
                    index++
                }
            }
        }
        values.add(value.toString())
        return values
    }

    private fun escapeListValue(value: String): String {
        if (value.none { it == LIST_SEPARATOR || it == LIST_ESCAPE }) return value
        return buildString(value.length + 1) {
            for (char in value) {
                if (char == LIST_SEPARATOR || char == LIST_ESCAPE) append(LIST_ESCAPE)
                append(char)
            }
        }
    }

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
