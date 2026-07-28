package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * One record exactly as a pre-#260 build wrote it: the fourteen columns of
 * [TransactionCsvFormat.V1], values matching `aTransaction(note = "milk")`.
 * Hard-coded rather than derived from the current encoder — the point is bytes
 * already sitting in a user's backup file.
 */
private val PRE_260_RECORD = listOf(
    "t-legacy",
    "ws-1",
    "a-1",
    "EXPENSE",
    "ACTUAL",
    "10000",
    "USD",
    "c-1",
    "milk",
    "2024-01-01T00:00:00Z",
    "2024-01-01",
    "2024-01-01T00:00:00Z",
    "2024-01-01T00:00:00Z",
    "",
)

class TransactionCsvCodecTest : StringSpec({

    fun decoded(fields: List<String>) =
        TransactionCsvCodec.decode(fields)
            .shouldBeInstanceOf<TransactionCsvDecodeResult.Decoded>()
            .transaction

    fun rejected(fields: List<String>) =
        TransactionCsvCodec.decode(fields)
            .shouldBeInstanceOf<TransactionCsvDecodeResult.Rejected>()
            .issue

    fun encodedField(transaction: Transaction, column: TransactionCsvColumn): String =
        TransactionCsvCodec.encode(transaction)[column.ordinal]

    "every field survives an encode/decode round-trip" {
        val transaction = aTransaction(
            id = transactionId("t-round"),
            note = "groceries",
            merchant = "Whole Foods",
            tags = listOf("food", "weekly"),
            type = TransactionType.INCOME,
            status = TransactionStatus.PLANNED,
            recurringRuleId = RecurringRuleId("rr-1"),
        ).copy(transferId = TransferId("tr-1"), splitId = SplitId("sp-1"))

        decoded(TransactionCsvCodec.encode(transaction)) shouldBe transaction
    }

    "null category transfer and recurring rule ids round-trip as empty fields" {
        val transaction = aTransaction(categoryId = null)

        val fields = TransactionCsvCodec.encode(transaction)

        fields[TransactionCsvColumn.CategoryId.ordinal] shouldBe ""
        fields[TransactionCsvColumn.TransferId.ordinal] shouldBe ""
        fields[TransactionCsvColumn.RecurringRuleId.ordinal] shouldBe ""
        fields[TransactionCsvColumn.SplitId.ordinal] shouldBe ""
        decoded(fields) shouldBe transaction
    }

    "an empty tag list encodes as an empty cell and decodes back to no tags" {
        val transaction = aTransaction(tags = emptyList())

        val fields = TransactionCsvCodec.encode(transaction)

        fields[TransactionCsvColumn.Tags.ordinal] shouldBe ""
        decoded(fields).tags shouldBe emptyList()
    }

    "tags share one cell separated by a pipe so the CSV delimiter stays untouched" {
        val fields = TransactionCsvCodec.encode(aTransaction(tags = listOf("food", "weekly")))

        fields[TransactionCsvColumn.Tags.ordinal] shouldBe "food|weekly"
        // One cell, so no quoting is forced and the record keeps its column count.
        Csv.encodeRecord(fields).count { it == ',' } shouldBe TransactionCsvCodec.header.size - 1
    }

    "a tag containing the in-cell separator survives instead of splitting in two" {
        val transaction = aTransaction(tags = listOf("a|b", "c"))

        val fields = TransactionCsvCodec.encode(transaction)

        fields[TransactionCsvColumn.Tags.ordinal] shouldBe "a\\|b|c"
        decoded(fields).tags shouldBe listOf("a|b", "c")
    }

    "a tag containing a literal escape character survives the round-trip" {
        val transaction = aTransaction(tags = listOf("back\\slash", "trailing\\"))

        decoded(TransactionCsvCodec.encode(transaction)).tags shouldBe
            listOf("back\\slash", "trailing\\")
    }

    "a tag cell ending in a dangling escape keeps it as a literal character" {
        // The encoder always doubles an escape, so only a hand-edited cell can
        // end in a lone one — decoding must not swallow the character with it.
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        fields[TransactionCsvColumn.Tags.ordinal] = "solo\\"

        decoded(fields).tags shouldBe listOf("solo\\")
    }

    "a merchant starting with a formula trigger is neutralised on encode" {
        listOf("=HYPERLINK(\"http://evil\")", "+1", "-1", "@SUM(A1)", "\tcmd", "\rx").forEach {
            encodedField(aTransaction(merchant = it), TransactionCsvColumn.Merchant) shouldBe "'$it"
        }
    }

    "an ordinary merchant is not prefixed" {
        encodedField(aTransaction(merchant = "Starbucks"), TransactionCsvColumn.Merchant) shouldBe
            "Starbucks"
    }

    "every tag in the cell is guarded on its own not just the first" {
        val fields = TransactionCsvCodec.encode(aTransaction(tags = listOf("safe", "=evil")))

        fields[TransactionCsvColumn.Tags.ordinal] shouldBe "safe|'=evil"
    }

    "dangerous merchants and tags survive a full CSV round-trip unchanged" {
        listOf("=cmd|' /C calc'!A0", "+1+1", "-danger", "@evil", "'=already quoted", "Starbucks")
            .forEach { text ->
                val transaction = aTransaction(merchant = text, tags = listOf(text, "plain"))

                val record = Csv.parseRecords(
                    Csv.encodeRecord(TransactionCsvCodec.encode(transaction)),
                ).single()

                decoded(record.fields).merchant shouldBe text
                decoded(record.fields).tags shouldBe listOf(text, "plain")
            }
    }

    "a hand-edited tag cell is re-normalised on the way in" {
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        // A comma would split the Room column these land in, blanks come from a
        // doubled separator, and the list is capped at TransactionTags.MAX_TAGS.
        fields[TransactionCsvColumn.Tags.ordinal] =
            "food,drink||  spaced  |" + (1..12).joinToString("|") { "t$it" }

        decoded(fields).tags shouldBe
            listOf("food drink", "spaced") + (1..8).map { "t$it" }
    }

    "note with commas quotes and newlines survives a full CSV round-trip" {
        val transaction = aTransaction(note = "milk, \"eggs\"\nand bread")

        val text = Csv.encodeRecord(TransactionCsvCodec.encode(transaction))
        val record = Csv.parseRecords(text).single()

        decoded(record.fields) shouldBe transaction
    }

    "a note starting with a formula trigger is neutralised on encode" {
        listOf("=HYPERLINK(\"http://evil\")", "+1", "-1", "@SUM(A1)", "\tcmd", "\rx").forEach { note ->
            val encoded = TransactionCsvCodec.encode(aTransaction(note = note))
            encoded[TransactionCsvColumn.Note.ordinal] shouldBe "'$note"
        }
    }

    "an ordinary note is not prefixed" {
        val encoded = TransactionCsvCodec.encode(aTransaction(note = "groceries"))

        encoded[TransactionCsvColumn.Note.ordinal] shouldBe "groceries"
    }

    "dangerous notes survive a full CSV round-trip unchanged" {
        listOf(
            "=cmd|' /C calc'!A0",
            "+1+1",
            "-danger",
            "@evil",
            "'=already quoted",
            "'plain apostrophe",
            "normal note",
        ).forEach { note ->
            val transaction = aTransaction(note = note)

            val text = Csv.encodeRecord(TransactionCsvCodec.encode(transaction))
            val record = Csv.parseRecords(text).single()

            decoded(record.fields).note shouldBe note
        }
    }

    "a legacy note starting with a literal apostrophe decodes unchanged" {
        // A pre-guard export (or a third-party CSV) wrote the note verbatim, so
        // the apostrophe is real data, not a guard prefix — it must survive.
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        fields[TransactionCsvColumn.Note.ordinal] = "'plain apostrophe"

        decoded(fields).note shouldBe "'plain apostrophe"
    }

    "wrong column count is rejected with expected and actual sizes" {
        rejected(listOf("only", "three", "fields")) shouldBe
            CsvRowIssue.ColumnCountMismatch(expected = TransactionCsvCodec.header.size, actual = 3)
    }

    "a record written by the pre-#260 format decodes with the new fields defaulted" {
        val decoded = TransactionCsvCodec.decode(PRE_260_RECORD, TransactionCsvFormat.V1)
            .shouldBeInstanceOf<TransactionCsvDecodeResult.Decoded>()
            .transaction

        decoded shouldBe aTransaction(id = transactionId("t-legacy"), note = "milk")
        decoded.merchant shouldBe ""
        decoded.tags shouldBe emptyList()
        decoded.recurringRuleId shouldBe null
        decoded.splitId shouldBe null
    }

    "a pre-#260 record read as the current format is rejected as a short row" {
        // The header row picks the layout, so a fourteen-field record inside a
        // current-format file is a truncated row, not a legacy one.
        rejected(PRE_260_RECORD) shouldBe CsvRowIssue.ColumnCountMismatch(
            expected = TransactionCsvCodec.header.size,
            actual = PRE_260_RECORD.size,
        )
    }

    "a current record read as the pre-#260 format is rejected as an over-long row" {
        val fields = TransactionCsvCodec.encode(aTransaction())

        TransactionCsvCodec.decode(fields, TransactionCsvFormat.V1)
            .shouldBeInstanceOf<TransactionCsvDecodeResult.Rejected>()
            .issue shouldBe CsvRowIssue.ColumnCountMismatch(
            expected = TransactionCsvFormat.V1.columns.size,
            actual = fields.size,
        )
    }

    "every published header row resolves back to its own layout" {
        TransactionCsvFormat.entries.forEach { format ->
            TransactionCsvFormat.forHeader(format.header) shouldBe format
        }
        TransactionCsvFormat.forHeader(TransactionCsvCodec.header) shouldBe
            TransactionCsvFormat.Latest
        TransactionCsvFormat.forHeader(listOf("date", "amount", "payee")) shouldBe null
    }

    "the newest layout writes every known column" {
        // Fails when a column is added to TransactionCsvColumn without a new
        // TransactionCsvFormat entry to write it — which is the whole point of
        // the format list, and also what keeps ordinal == index for the layout
        // this build writes.
        TransactionCsvFormat.Latest.columns shouldBe TransactionCsvColumn.entries.toList()
    }

    "older layouts keep their own field count when a newer one exists" {
        // Each layout describes bytes already on disk, so growing the newest one
        // must never grow an older one.
        TransactionCsvFormat.V1.columns.size shouldBe 14
        TransactionCsvFormat.V1.header.last() shouldBe "transfer_id"
    }

    "only the pre-#260 layout is missing the fields #260 added" {
        val added = listOf(
            TransactionCsvColumn.Merchant,
            TransactionCsvColumn.Tags,
            TransactionCsvColumn.RecurringRuleId,
        )

        added.forEach { TransactionCsvFormat.V1.indexOf(it) shouldBe null }
        added.forEach { TransactionCsvFormat.Latest.indexOf(it) shouldBe it.ordinal }
    }

    "split_id is absent from every layout that predates splits" {
        // A backup written before #399 decodes to a transaction with no split, rather than
        // failing: the column is simply not in that file's layout.
        TransactionCsvFormat.V1.indexOf(TransactionCsvColumn.SplitId) shouldBe null
        TransactionCsvFormat.V2.indexOf(TransactionCsvColumn.SplitId) shouldBe null
        TransactionCsvFormat.V2.columns.size shouldBe 17
        TransactionCsvFormat.Latest.indexOf(TransactionCsvColumn.SplitId) shouldBe
            TransactionCsvColumn.SplitId.ordinal
    }

    "a leg's split id survives a V2 file being re-imported into the current layout" {
        val leg = aTransaction(id = transactionId("t-leg")).copy(splitId = SplitId("sp-1"))

        val fields = TransactionCsvCodec.encode(leg)

        fields[TransactionCsvColumn.SplitId.ordinal] shouldBe "sp-1"
        decoded(fields).splitId shouldBe SplitId("sp-1")
    }

    "unparseable amount is rejected as invalid amount_minor" {
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        fields[TransactionCsvColumn.AmountMinor.ordinal] = "12.50"

        rejected(fields) shouldBe CsvRowIssue.InvalidValue("amount_minor")
    }

    "amount_minor at the domain cap is accepted" {
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        fields[TransactionCsvColumn.AmountMinor.ordinal] = Money.MAX_MINOR.toString()

        decoded(fields).money shouldBe Money.fromMinor(Money.MAX_MINOR)
    }

    "amount_minor beyond the domain cap is rejected as invalid amount_minor" {
        listOf(
            (Money.MAX_MINOR + 1).toString(),
            (-Money.MAX_MINOR - 1).toString(),
            Long.MAX_VALUE.toString(),
            Long.MIN_VALUE.toString(),
        ).forEach { crafted ->
            val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
            fields[TransactionCsvColumn.AmountMinor.ordinal] = crafted

            rejected(fields) shouldBe CsvRowIssue.InvalidValue("amount_minor")
        }
    }

    "unknown transaction type is rejected as invalid type" {
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        fields[TransactionCsvColumn.Type.ordinal] = "GIFT"

        rejected(fields) shouldBe CsvRowIssue.InvalidValue("type")
    }

    "unparseable operation date is rejected as invalid operation_date" {
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        fields[TransactionCsvColumn.OperationDate.ordinal] = "01/02/2024"

        rejected(fields) shouldBe CsvRowIssue.InvalidValue("operation_date")
    }

    "unparseable operation instant is rejected as invalid operation_at" {
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        fields[TransactionCsvColumn.OperationAt.ordinal] = "yesterday"

        rejected(fields) shouldBe CsvRowIssue.InvalidValue("operation_at")
    }

    "blank id is rejected as invalid id" {
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        fields[TransactionCsvColumn.Id.ordinal] = ""

        rejected(fields) shouldBe CsvRowIssue.InvalidValue("id")
    }

    "blank category id decodes to null instead of failing" {
        val fields = TransactionCsvCodec.encode(aTransaction(categoryId = categoryId())).toMutableList()
        fields[TransactionCsvColumn.CategoryId.ordinal] = ""

        decoded(fields).categoryId shouldBe null
    }

    "parser handles CRLF line endings a UTF-8 BOM and blank lines" {
        val text = "\uFEFFa,b\r\n\r\nc,d\r\n"

        val records = Csv.parseRecords(text)

        records.map { it.fields } shouldBe listOf(listOf("a", "b"), listOf("c", "d"))
        // The blank line still occupies row 2, so the second record is row 3.
        records.map { it.rowNumber } shouldBe listOf(1, 3)
    }

    "quoted field with embedded newline advances the row number by one record" {
        val text = "\"multi\nline\",x\nsecond,y\n"

        val records = Csv.parseRecords(text)

        records[0].fields shouldBe listOf("multi\nline", "x")
        records[1].rowNumber shouldBe 2
    }
})
