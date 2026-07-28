package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okio.Buffer

/**
 * A backup file exactly as a pre-#260 build wrote it — header bytes included,
 * spelled out rather than derived from [TransactionCsvFormat], because renaming
 * a column header would silently orphan files already on users' disks and this
 * test should fail when that happens. The row's values match
 * `aTransaction(id = transactionId("t-legacy"), note = "milk")`.
 */
private const val PRE_260_HEADER =
    "id,workspace_id,account_id,type,status,amount_minor,currency,category_id,note," +
        "operation_at,operation_date,created_at,updated_at,transfer_id"

private val PRE_260_ROW = listOf(
    "t-legacy", "ws-1", "a-1", "EXPENSE", "ACTUAL", "10000", "USD", "c-1", "milk",
    "2024-01-01T00:00:00Z", "2024-01-01", "2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "",
).joinToString(",")

/**
 * Export, round-trip and duplicate handling. Per-row validation and whole-file failures live in
 * [ImportTransactionsValidationTest].
 */
class ExportImportTransactionsUseCaseTest : StringSpec({

    "export writes a header plus one record per transaction and returns the count" {
        runTest {
            val stack = CsvStack(
                transactions = listOf(
                    aTransaction(id = transactionId("t-1")),
                    aTransaction(id = transactionId("t-2"), note = "with, comma"),
                ),
            )
            val buffer = Buffer()

            stack.export(buffer).getOrThrow() shouldBe 2

            val lines = buffer.readUtf8().trimEnd().lines()
            lines.first() shouldBe Csv.encodeRecord(TransactionCsvCodec.header)
            lines.size shouldBe 3
        }
    }

    "round-trip: an exported file imports every transaction into an empty database" {
        runTest {
            val transactions = listOf(
                aTransaction(id = transactionId("t-1"), note = "milk, \"eggs\"\nand bread"),
                aTransaction(id = transactionId("t-2"), categoryId = null),
            )
            val csv = exportedCsv(*transactions.toTypedArray())
            val stack = CsvStack()

            val report = stack.import(csv).getOrThrow()

            report shouldBe CsvImportReport(imported = 2, skippedDuplicates = 0, errors = emptyList())
            stack.transactionRepository.stored() shouldContainExactlyInAnyOrder transactions
        }
    }

    "round-trip: merchant tags and recurring rule id survive export and import" {
        runTest {
            val transactions = listOf(
                aTransaction(
                    id = transactionId("t-1"),
                    merchant = "Starbucks, \"the good one\"",
                    tags = listOf("coffee", "=formula|piped"),
                    recurringRuleId = RecurringRuleId("rr-1"),
                ),
                aTransaction(id = transactionId("t-2"), merchant = "", tags = emptyList()),
            )
            val stack = CsvStack()

            val report = stack.import(exportedCsv(*transactions.toTypedArray())).getOrThrow()

            report shouldBe CsvImportReport(imported = 2, skippedDuplicates = 0, errors = emptyList())
            stack.transactionRepository.stored() shouldContainExactlyInAnyOrder transactions
        }
    }

    "a backup written by the pre-#260 build imports with the new fields defaulted" {
        runTest {
            val stack = CsvStack()

            val report = stack.import("$PRE_260_HEADER\n$PRE_260_ROW\n").getOrThrow()

            report shouldBe CsvImportReport(imported = 1, skippedDuplicates = 0, errors = emptyList())
            stack.transactionRepository.stored().single() shouldBe
                aTransaction(id = transactionId("t-legacy"), note = "milk")
        }
    }

    "a truncated row inside a pre-#260 file is still rejected" {
        runTest {
            val truncated = PRE_260_ROW.substringBeforeLast(",")
            val stack = CsvStack()

            val report = stack.import("$PRE_260_HEADER\n$truncated\n$PRE_260_ROW\n").getOrThrow()

            report.imported shouldBe 1
            report.errors shouldBe listOf(
                CsvRowError(
                    rowNumber = 2,
                    issue = CsvRowIssue.ColumnCountMismatch(
                        expected = TransactionCsvFormat.V1.columns.size,
                        actual = TransactionCsvFormat.V1.columns.size - 1,
                    ),
                ),
            )
        }
    }

    "import skips rows whose id already exists locally" {
        runTest {
            val existing = aTransaction(id = transactionId("t-1"))
            val fresh = aTransaction(id = transactionId("t-2"))
            val csv = exportedCsv(existing, fresh)
            val stack = CsvStack(transactions = listOf(existing))

            val report = stack.import(csv).getOrThrow()

            report.imported shouldBe 1
            report.skippedDuplicates shouldBe 1
            report.errors shouldBe emptyList()
        }
    }

    "import skips duplicate ids inside the same file" {
        runTest {
            val transaction = aTransaction(id = transactionId("t-dup"))
            val row = Csv.encodeRecord(TransactionCsvCodec.encode(transaction))
            val csv = Csv.encodeRecord(TransactionCsvCodec.header) + "\n" + row + "\n" + row + "\n"
            val stack = CsvStack()

            val report = stack.import(csv).getOrThrow()

            report.imported shouldBe 1
            report.skippedDuplicates shouldBe 1
            stack.transactionRepository.stored().size shouldBe 1
        }
    }

    "malformed and invalid rows are reported with row numbers and do not stop the import" {
        runTest {
            val good = aTransaction(id = transactionId("t-good"))
            val header = Csv.encodeRecord(TransactionCsvCodec.header)
            val goodRow = Csv.encodeRecord(TransactionCsvCodec.encode(good))
            val badAmount = Csv.encodeRecord(
                TransactionCsvCodec.encode(aTransaction(id = transactionId("t-bad")))
                    .toMutableList()
                    .also { it[TransactionCsvColumn.AmountMinor.ordinal] = "not-a-number" },
            )
            val csv = "$header\ntoo,few,columns\n$badAmount\n$goodRow\n"
            val stack = CsvStack()

            val report = stack.import(csv).getOrThrow()

            report.imported shouldBe 1
            report.errors shouldBe listOf(
                CsvRowError(
                    rowNumber = 2,
                    issue = CsvRowIssue.ColumnCountMismatch(
                        expected = TransactionCsvCodec.header.size,
                        actual = 3,
                    ),
                ),
                CsvRowError(rowNumber = 3, issue = CsvRowIssue.InvalidValue("amount_minor")),
            )
            stack.transactionRepository.stored().single().id shouldBe transactionId("t-good")
        }
    }
})
