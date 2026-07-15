package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TransactionCsvCodecTest : StringSpec({

    fun decoded(fields: List<String>) =
        TransactionCsvCodec.decode(fields)
            .shouldBeInstanceOf<TransactionCsvDecodeResult.Decoded>()
            .transaction

    fun rejected(fields: List<String>) =
        TransactionCsvCodec.decode(fields)
            .shouldBeInstanceOf<TransactionCsvDecodeResult.Rejected>()
            .issue

    "every field survives an encode/decode round-trip" {
        val transaction = aTransaction(
            id = transactionId("t-round"),
            note = "groceries",
            type = TransactionType.INCOME,
            status = TransactionStatus.PLANNED,
        ).copy(transferId = TransferId("tr-1"))

        decoded(TransactionCsvCodec.encode(transaction)) shouldBe transaction
    }

    "null category and transfer ids round-trip as empty fields" {
        val transaction = aTransaction(categoryId = null)

        val fields = TransactionCsvCodec.encode(transaction)

        fields[TransactionCsvColumn.CategoryId.ordinal] shouldBe ""
        fields[TransactionCsvColumn.TransferId.ordinal] shouldBe ""
        decoded(fields) shouldBe transaction
    }

    "note with commas quotes and newlines survives a full CSV round-trip" {
        val transaction = aTransaction(note = "milk, \"eggs\"\nand bread")

        val text = Csv.encodeRecord(TransactionCsvCodec.encode(transaction))
        val record = Csv.parseRecords(text).single()

        decoded(record.fields) shouldBe transaction
    }

    "wrong column count is rejected with expected and actual sizes" {
        rejected(listOf("only", "three", "fields")) shouldBe
            CsvRowIssue.ColumnCountMismatch(expected = TransactionCsvCodec.header.size, actual = 3)
    }

    "unparseable amount is rejected as invalid amount_minor" {
        val fields = TransactionCsvCodec.encode(aTransaction()).toMutableList()
        fields[TransactionCsvColumn.AmountMinor.ordinal] = "12.50"

        rejected(fields) shouldBe CsvRowIssue.InvalidValue("amount_minor")
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
