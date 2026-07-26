package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import okio.buffer

/**
 * What an import refuses: rows pointing at data that isn't there, transfers that would land
 * half-imported, and files that are not this app's CSV at all. Export and round-trip live in
 * [ExportImportTransactionsUseCaseTest].
 */
class ImportTransactionsValidationTest : StringSpec({

    "rows referencing unknown workspace account or category are reported per row" {
        runTest {
            val unknownWorkspace = aTransaction(
                id = transactionId("t-ws"),
                workspaceId = WorkspaceId("ws-missing"),
            )
            val unknownAccount = aTransaction(
                id = transactionId("t-acc"),
                accountId = AccountId("a-missing"),
            )
            val unknownCategory = aTransaction(
                id = transactionId("t-cat"),
                categoryId = CategoryId("c-missing"),
            )
            val csv = exportedCsv(unknownWorkspace, unknownAccount, unknownCategory)
            val stack = CsvStack()

            val report = stack.import(csv).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.UnknownWorkspace("ws-missing")),
                CsvRowError(3, CsvRowIssue.UnknownAccount("a-missing")),
                CsvRowError(4, CsvRowIssue.UnknownCategory("c-missing")),
            )
        }
    }

    "a row whose account belongs to a different workspace is rejected" {
        runTest {
            val stack = CsvStack(
                workspaces = listOf(aWorkspace(), aWorkspace(id = workspaceId("ws-2"))),
                accounts = listOf(
                    anAccount(),
                    anAccount(id = accountId("a-2"), workspaceId = workspaceId("ws-2")),
                ),
            )
            val foreignAccount = aTransaction(accountId = accountId("a-2"))

            val report = stack.import(exportedCsv(foreignAccount)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(
                    2,
                    CsvRowIssue.AccountWorkspaceMismatch(accountId = "a-2", workspaceId = "ws-1"),
                ),
            )
            stack.transactionRepository.stored() shouldBe emptyList()
        }
    }

    "a row whose category belongs to a different workspace is rejected" {
        runTest {
            val stack = CsvStack(
                workspaces = listOf(aWorkspace(), aWorkspace(id = workspaceId("ws-2"))),
                categories = listOf(
                    aCategory(),
                    aCategory(id = categoryId("c-2"), workspaceId = workspaceId("ws-2")),
                ),
            )
            val foreignCategory = aTransaction(categoryId = categoryId("c-2"))

            val report = stack.import(exportedCsv(foreignCategory)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(
                    2,
                    CsvRowIssue.CategoryWorkspaceMismatch(categoryId = "c-2", workspaceId = "ws-1"),
                ),
            )
        }
    }

    "a row whose currency differs from the account currency is rejected" {
        runTest {
            val stack = CsvStack()
            val wrongCurrency = aTransaction(currencyCode = EUR)

            val report = stack.import(exportedCsv(wrongCurrency)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.CurrencyMismatch(currency = "EUR", accountCurrency = "USD")),
            )
            stack.transactionRepository.stored() shouldBe emptyList()
        }
    }

    "a consistent transfer pair imports both legs" {
        runTest {
            val stack = CsvStack(accounts = listOf(anAccount(), anAccount(id = accountId("a-2"))))
            val transfer = TransferId("tr-1")
            val outLeg = aTransaction(
                id = transactionId("t-out"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )
            val inLeg = aTransaction(
                id = transactionId("t-in"),
                accountId = accountId("a-2"),
                type = TransactionType.INCOME,
                transferId = transfer,
            )

            val report = stack.import(exportedCsv(outLeg, inLeg)).getOrThrow()

            report shouldBe CsvImportReport(imported = 2, skippedDuplicates = 0, errors = emptyList())
        }
    }

    "a transfer leg without its pair row is rejected" {
        runTest {
            val stack = CsvStack()
            val loneLeg = aTransaction(transferId = TransferId("tr-1"))

            val report = stack.import(exportedCsv(loneLeg)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.UnpairedTransfer("tr-1")),
            )
            stack.transactionRepository.stored() shouldBe emptyList()
        }
    }

    "a transfer pair with two legs of the same type is rejected" {
        runTest {
            val stack = CsvStack(accounts = listOf(anAccount(), anAccount(id = accountId("a-2"))))
            val transfer = TransferId("tr-1")
            val first = aTransaction(
                id = transactionId("t-1"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )
            val second = aTransaction(
                id = transactionId("t-2"),
                accountId = accountId("a-2"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )

            val report = stack.import(exportedCsv(first, second)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.UnpairedTransfer("tr-1")),
                CsvRowError(3, CsvRowIssue.UnpairedTransfer("tr-1")),
            )
        }
    }

    "a transfer leg pairs with its duplicate counterpart already in the database" {
        runTest {
            val transfer = TransferId("tr-1")
            val outLeg = aTransaction(
                id = transactionId("t-out"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )
            val inLeg = aTransaction(
                id = transactionId("t-in"),
                accountId = accountId("a-2"),
                type = TransactionType.INCOME,
                transferId = transfer,
            )
            val stack = CsvStack(
                accounts = listOf(anAccount(), anAccount(id = accountId("a-2"))),
                transactions = listOf(outLeg),
            )

            val report = stack.import(exportedCsv(outLeg, inLeg)).getOrThrow()

            report shouldBe CsvImportReport(imported = 1, skippedDuplicates = 1, errors = emptyList())
        }
    }

    "a transfer pair with one invalid leg does not import the surviving half" {
        runTest {
            val stack = CsvStack(accounts = listOf(anAccount(), anAccount(id = accountId("a-2"))))
            val transfer = TransferId("tr-1")
            val goodLeg = aTransaction(
                id = transactionId("t-good"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )
            val badLeg = aTransaction(
                id = transactionId("t-bad"),
                accountId = accountId("a-2"),
                currencyCode = EUR,
                type = TransactionType.INCOME,
                transferId = transfer,
            )

            val report = stack.import(exportedCsv(goodLeg, badLeg)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.UnpairedTransfer("tr-1")),
                CsvRowError(3, CsvRowIssue.CurrencyMismatch(currency = "EUR", accountCurrency = "USD")),
            )
            stack.transactionRepository.stored() shouldBe emptyList()
        }
    }

    "a foreign CSV header fails the whole import" {
        runTest {
            val stack = CsvStack()

            val error = stack.import("date,amount,payee\n2024-01-01,12,ACME\n")
                .exceptionOrNull()

            error.shouldBeInstanceOf<TransactionCsvError.HeaderMismatch>()
        }
    }

    "an empty file fails the whole import" {
        runTest {
            val stack = CsvStack()

            stack.import("").exceptionOrNull()
                .shouldBeInstanceOf<TransactionCsvError.EmptyFile>()
        }
    }

    "an oversized source is rejected without being fully read into memory" {
        runTest {
            val stack = CsvStack()
            // A 1 GiB stream generated lazily on demand — if the guard read it
            // whole it would exhaust the heap. The assertions below prove it
            // stops just past the 16 MiB cap instead.
            val huge = CountingSource(total = 1L * 1024 * 1024 * 1024)

            val error = stack.import(huge.buffer()).exceptionOrNull()

            error.shouldBeInstanceOf<TransactionCsvError.FileTooLarge>()
            stack.transactionRepository.stored() shouldBe emptyList()
            // Read only a hair over the cap, never the full gigabyte.
            huge.produced shouldBeLessThan 17L * 1024 * 1024
        }
    }
})
