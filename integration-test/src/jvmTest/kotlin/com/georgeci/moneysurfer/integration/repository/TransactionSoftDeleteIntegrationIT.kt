package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.data.db.FtsQuery
import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.domain.csv.ExportTransactionsUseCase
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.PurgeDeletedTransactionsUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateTransactionUseCase
import com.georgeci.moneysurfer.domain.util.periodWindow
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import okio.Buffer
import kotlin.time.Instant

private val OPERATION_DATE = LocalDate(2026, 3, 15)
private val MONTH = periodWindow(TransactionPeriodMode.Month, OPERATION_DATE)
private const val MONTH_START = "2026-03-01"
private const val NEXT_MONTH_START = "2026-04-01"

/**
 * A tombstoned transaction has to be invisible everywhere at once. The acceptance criterion of
 * issue #346 names the read paths individually — list window, totals, FTS search, CSV backup,
 * dashboard widgets — because they are separate SQL statements that each carry their own copy of
 * the filter, and one of them forgetting it is exactly the bug this test exists to catch.
 *
 * Every assertion is made three times over the same row: while it is live, once it is deleted, and
 * again after Undo. Checking only the middle state would pass just as happily against a query that
 * returns nothing at all.
 */
class TransactionSoftDeleteIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: FinanceStack
    lateinit var dao: TransactionDao
    lateinit var deleteTransaction: DeleteTransactionUseCase

    val account = accountId("a-1")
    val target = transactionId("t-deleted")
    val survivor = transactionId("t-kept")

    /** Every read path the acceptance criteria name, as the ids each one currently returns. */
    suspend fun visibleIds(): Map<String, List<String>> = mapOf(
        "getAll" to stack.transactionRepository.getAll().first().map { it.id.value },
        "getByAccountId" to stack.transactionRepository.getByAccountId(account).first().map { it.id.value },
        "getByWorkspaceId" to stack.transactionRepository
            .getByWorkspaceId(DEFAULT_WORKSPACE_ID).first().map { it.id.value },
        "listWindow" to stack.transactionRepository
            .getCategorizedWindow(accountId = null, window = MONTH, limit = 100)
            .first().map { it.transaction.id.value },
        "ftsSearch" to dao
            .searchByText(WORKSPACE_ID_VALUE, checkNotNull(FtsQuery.fromUserInput("coffee")))
            .first().map { it.id },
        "spendByCategory" to dao
            .getSpendByCategory(WORKSPACE_ID_VALUE, "USD", MONTH_START, NEXT_MONTH_START)
            .first().map { "${it.categoryId}:${it.totalMinor}" },
        "dailySpend" to dao
            .getDailySpend(WORKSPACE_ID_VALUE, "USD", MONTH_START, NEXT_MONTH_START)
            .first().map { "${it.operationDate}:${it.totalMinor}" },
        "topMerchants" to dao
            .getTopMerchants(WORKSPACE_ID_VALUE, "USD", MONTH_START, NEXT_MONTH_START, limit = 10)
            .first().map { "${it.merchant}:${it.totalMinor}" },
        "netByMonth" to dao
            .getNetTotalsByMonth(WORKSPACE_ID_VALUE, "USD", MONTH_START, NEXT_MONTH_START)
            .first().map { "${it.month}:${it.totalMinor}" },
        "monthlyTotalsByCategory" to dao
            .getMonthlyTotalsByCategory(
                workspaceId = WORKSPACE_ID_VALUE,
                categoryIds = listOf("c-1"),
                type = TransactionType.EXPENSE.name,
                baseCurrency = "USD",
                fromDate = MONTH_START,
                toDateExclusive = NEXT_MONTH_START,
            )
            .first().map { "${it.month}:${it.totalMinor}" },
    )

    suspend fun exportedIds(): List<String> {
        val buffer = Buffer()
        ExportTransactionsUseCase(stack.transactionRepository)(buffer).getOrThrow()
        return buffer.readUtf8().lines().drop(1).filter { it.isNotBlank() }.map { it.substringBefore(',') }
    }

    beforeEach {
        harness = IntegrationHarness()
        stack = FinanceStack(harness)
        dao = harness.database.transactionDao()
        // The use case, not the bare repository: a delete moves the account balance too, and
        // the restore assertions below are only meaningful against the same path.
        deleteTransaction = DeleteTransactionUseCase(stack.transactionRepository, stack.applyTransactionChange)
        harness.seedWorkspace()
        stack.accountRepository.insert(
            anAccount(
                id = account,
                workspaceId = DEFAULT_WORKSPACE_ID,
                currencyCode = USD,
                balance = 500.dollars,
            ),
        )
        stack.categoryRepository.insert(aCategory(id = categoryId("c-1"), workspaceId = DEFAULT_WORKSPACE_ID))
        // Both rows share a category, a merchant and a day, so an aggregate that forgot the filter
        // still reports a bucket — with the wrong total — instead of vanishing.
        listOf(target, survivor).forEach { id ->
            stack.createTransaction(
                aTransaction(
                    id = id,
                    workspaceId = DEFAULT_WORKSPACE_ID,
                    accountId = account,
                    money = 40.dollars,
                    currencyCode = USD,
                    categoryId = categoryId("c-1"),
                    note = "coffee",
                    merchant = "Beans",
                    operationDate = OPERATION_DATE,
                    type = TransactionType.EXPENSE,
                ),
            )
        }
    }

    afterEach { harness.close() }

    "a deleted transaction leaves every read path, and Undo puts it back in all of them" {
        val whileLive = visibleIds()
        whileLive.getValue("getAll") shouldContainExactly listOf(target.value, survivor.value)
        whileLive.getValue("spendByCategory") shouldContainExactly listOf("c-1:8000")

        deleteTransaction(target)

        val whileDeleted = visibleIds()
        whileDeleted.getValue("getAll") shouldContainExactly listOf(survivor.value)
        whileDeleted.getValue("getByAccountId") shouldContainExactly listOf(survivor.value)
        whileDeleted.getValue("getByWorkspaceId") shouldContainExactly listOf(survivor.value)
        whileDeleted.getValue("listWindow") shouldContainExactly listOf(survivor.value)
        whileDeleted.getValue("ftsSearch") shouldContainExactly listOf(survivor.value)
        // Halved, not gone: the surviving row still spends in the same bucket.
        whileDeleted.getValue("spendByCategory") shouldContainExactly listOf("c-1:4000")
        whileDeleted.getValue("dailySpend") shouldContainExactly listOf("2026-03-15:4000")
        whileDeleted.getValue("topMerchants") shouldContainExactly listOf("Beans:4000")
        whileDeleted.getValue("netByMonth") shouldContainExactly listOf("2026-03:4000")
        whileDeleted.getValue("monthlyTotalsByCategory") shouldContainExactly listOf("2026-03:4000")

        stack.applyTransactionChange.restore(target).shouldNotBeNull()

        visibleIds() shouldBe whileLive
    }

    "the CSV backup exports live rows only, and exports the row again once it is restored" {
        exportedIds() shouldContainExactly listOf(target.value, survivor.value)

        deleteTransaction(target)
        exportedIds() shouldContainExactly listOf(survivor.value)

        stack.applyTransactionChange.restore(target)
        exportedIds() shouldContainExactly listOf(target.value, survivor.value)
    }

    "totals over the window drop the deleted row's money and get it back on Undo" {
        suspend fun expenseTotal() = stack.transactionRepository
            .getTotals(accountId = account, window = MONTH)
            .first()
            .single { it.type == TransactionType.EXPENSE }
            .total

        expenseTotal() shouldBe 80.dollars

        deleteTransaction(target)
        expenseTotal() shouldBe 40.dollars

        stack.applyTransactionChange.restore(target)
        expenseTotal() shouldBe 80.dollars
    }

    // The row is not re-created on the way back, so everything the user typed — and everything the
    // database decided for them — is necessarily identical. A re-insert could not promise that:
    // `createdAt` alone would come from whatever copy the caller happened to be holding.
    "Undo restores the same row rather than an equal-looking new one" {
        val before = dao.getById(target.value).shouldNotBeNull()

        deleteTransaction(target)
        stack.applyTransactionChange.restore(target)

        val after = dao.getById(target.value).shouldNotBeNull()
        // `updatedAt` legitimately moves with the delete and the restore — it is what LWW compares.
        after.copy(updatedAt = before.updatedAt) shouldBe before
    }

    "a second Undo is a no-op rather than a second refund" {
        deleteTransaction(target)
        stack.accountRepository.getById(account)!!.balance shouldBe 460.dollars

        stack.applyTransactionChange.restore(target).shouldNotBeNull()
        stack.applyTransactionChange.restore(target) shouldBe null

        stack.accountRepository.getById(account)!!.balance shouldBe 420.dollars
    }

    "purging drops tombstones past the retention window and leaves the rest alone" {
        val retention = PurgeDeletedTransactionsUseCase.RETENTION
        deleteTransaction(target)

        // Threshold below the tombstone's age: nothing is due yet.
        stack.retention.purgeDeletedBefore(Instant.fromEpochMilliseconds(0)) shouldBe 0
        dao.getByIdIncludingDeleted(target.value).shouldNotBeNull()

        stack.retention.purgeDeletedBefore(stack.clock.now() + retention) shouldBe 1

        dao.getByIdIncludingDeleted(target.value) shouldBe null
        dao.getById(survivor.value).shouldNotBeNull()
    }

    "a purged row can no longer be restored — the Undo simply finds nothing" {
        deleteTransaction(target)
        stack.retention.purgeDeletedBefore(stack.clock.now() + PurgeDeletedTransactionsUseCase.RETENTION)

        stack.applyTransactionChange.restore(target) shouldBe null

        stack.transactionRepository.getAll().first().map { it.id } shouldContainExactly listOf(survivor)
    }

    // The edit screen is open, the row is deleted underneath it — by a pulled delete from another
    // device, or by a swipe on a list still in the back stack — and then the user presses Save.
    // A delete no longer frees the id, so treating "no live row" as "create" would collide with the
    // surviving primary key and fail the save with the balance already moved.
    "saving an edit to a row deleted underneath it restores the row instead of colliding" {
        val updateTransaction = UpdateTransactionUseCase(stack.transactionRepository, stack.applyTransactionChange)
        val edited = dao.getById(target.value).shouldNotBeNull()

        deleteTransaction(target)
        stack.accountRepository.getById(account)!!.balance shouldBe 460.dollars

        updateTransaction(
            aTransaction(
                id = target,
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = account,
                money = 10.dollars,
                currencyCode = USD,
                categoryId = categoryId("c-1"),
                note = "coffee",
                merchant = "Beans",
                operationDate = OPERATION_DATE,
                type = TransactionType.EXPENSE,
            ),
        )

        val saved = stack.transactionRepository.getById(target).shouldNotBeNull()
        saved.money shouldBe 10.dollars
        // 500 − 40 (survivor) − 10 (the edit), with the deleted row's 40 refunded exactly once.
        stack.accountRepository.getById(account)!!.balance shouldBe 450.dollars
        // Restored, not re-created: the row kept the createdAt it was originally written with.
        dao.getById(target.value).shouldNotBeNull().createdAt shouldBe edited.createdAt
    }

    "saving an edit to a row that was already purged inserts it fresh" {
        val updateTransaction = UpdateTransactionUseCase(stack.transactionRepository, stack.applyTransactionChange)
        deleteTransaction(target)
        stack.retention.purgeDeletedBefore(stack.clock.now() + PurgeDeletedTransactionsUseCase.RETENTION)

        updateTransaction(
            aTransaction(
                id = target,
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = account,
                money = 10.dollars,
                currencyCode = USD,
                categoryId = categoryId("c-1"),
                operationDate = OPERATION_DATE,
                type = TransactionType.EXPENSE,
            ),
        )

        stack.transactionRepository.getById(target).shouldNotBeNull().money shouldBe 10.dollars
        stack.accountRepository.getById(account)!!.balance shouldBe 450.dollars
    }

    // `@Update` rewrites every column, and the domain model has no tombstone field — so without the
    // read-back in TransactionRepositoryImpl.update an unrelated edit would quietly undelete a row.
    "updating a tombstoned row through the repository leaves it deleted" {
        deleteTransaction(target)

        stack.transactionRepository.update(
            aTransaction(
                id = target,
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = account,
                money = 10.dollars,
                currencyCode = USD,
                categoryId = categoryId("c-1"),
                operationDate = OPERATION_DATE,
                type = TransactionType.EXPENSE,
            ),
        )

        stack.transactionRepository.getById(target) shouldBe null
        dao.getByIdIncludingDeleted(target.value).shouldNotBeNull().deletedAt.shouldNotBeNull()
    }
})
