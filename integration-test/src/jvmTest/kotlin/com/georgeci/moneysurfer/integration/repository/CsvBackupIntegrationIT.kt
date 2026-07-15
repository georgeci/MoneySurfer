package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.domain.csv.CsvRowIssue
import com.georgeci.moneysurfer.domain.csv.ExportTransactionsUseCase
import com.georgeci.moneysurfer.domain.csv.ImportTransactionsUseCase
import com.georgeci.moneysurfer.domain.csv.TransactionCsvCodec
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import okio.Buffer

/**
 * Drives the CSV export/import use cases against a real Room database: rows
 * must be present after import, duplicates must be skipped by id, reference
 * errors must be reported per row, and cached account balances must reflect
 * the imported transactions (import goes through `CreateTransactionUseCase`).
 */
class CsvBackupIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: FinanceStack
    lateinit var export: ExportTransactionsUseCase
    lateinit var import: ImportTransactionsUseCase

    beforeEach {
        harness = IntegrationHarness()
        stack = FinanceStack(harness)
        harness.seedWorkspace()
        export = ExportTransactionsUseCase(stack.transactionRepository)
        import = ImportTransactionsUseCase(
            transactionRepository = stack.transactionRepository,
            accountRepository = stack.accountRepository,
            categoryRepository = stack.categoryRepository,
            workspaceRepository = stack.workspaceRepository,
            createTransaction = stack.createTransaction,
        )
        stack.accountRepository.insert(
            anAccount(
                id = accountId("a-1"),
                workspaceId = DEFAULT_WORKSPACE_ID,
                currencyCode = USD,
                balance = 100.dollars,
            ),
        )
        stack.categoryRepository.insert(
            aCategory(id = categoryId("c-1"), workspaceId = DEFAULT_WORKSPACE_ID),
        )
    }

    afterEach { harness.close() }

    "export then import restores a deleted transaction and skips the surviving one" {
        val kept = aTransaction(
            id = transactionId("t-kept"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            note = "kept, with \"quotes\"",
        )
        val deleted = aTransaction(
            id = transactionId("t-deleted"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            money = 25.dollars,
            type = TransactionType.EXPENSE,
        )
        stack.createTransaction(kept)
        stack.createTransaction(deleted)

        val buffer = Buffer()
        export(buffer).getOrThrow() shouldBe 2
        stack.transactionRepository.delete(deleted.id)

        val report = import(buffer).getOrThrow()

        report.imported shouldBe 1
        report.skippedDuplicates shouldBe 1
        report.errors shouldBe emptyList()
        stack.transactionRepository.getAll().first()
            .map { it.id } shouldContainExactlyInAnyOrder listOf(kept.id, deleted.id)
        stack.transactionRepository.getById(deleted.id) shouldNotBe null
    }

    "imported expense adjusts the cached account balance" {
        val expense = aTransaction(
            id = transactionId("t-exp"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            money = 30.dollars,
            type = TransactionType.EXPENSE,
        )
        val buffer = Buffer()
        stack.createTransaction(expense)
        export(buffer).getOrThrow()
        stack.transactionRepository.delete(expense.id)
        stack.accountRepository.setBalance(accountId("a-1"), 100.dollars)

        import(buffer).getOrThrow().imported shouldBe 1

        stack.accountRepository.getById(accountId("a-1"))!!.balance shouldBe 70.dollars
    }

    "row referencing a missing account is reported and not inserted" {
        val valid = aTransaction(id = transactionId("t-valid"), workspaceId = DEFAULT_WORKSPACE_ID)
        val orphan = aTransaction(
            id = transactionId("t-orphan"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            accountId = AccountId("a-missing"),
        )
        stack.createTransaction(valid)
        val buffer = Buffer()
        export(buffer).getOrThrow()
        stack.transactionRepository.delete(valid.id)

        // Append the orphan row by hand — it can't be round-tripped through
        // Room because its account FK doesn't exist. Fields contain no commas,
        // so plain joinToString is valid CSV here.
        val csvText = buffer.readUtf8() +
            TransactionCsvCodec.encode(orphan).joinToString(",") + "\n"
        val report = import(Buffer().writeUtf8(csvText)).getOrThrow()

        report.imported shouldBe 1
        report.errors.single().rowNumber shouldBe 3
        report.errors.single().issue shouldBe CsvRowIssue.UnknownAccount("a-missing")
        stack.transactionRepository.getById(valid.id) shouldNotBe null
        stack.transactionRepository.getById(orphan.id) shouldBe null
    }
})
