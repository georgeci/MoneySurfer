package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Clock
import kotlin.time.Instant

private val TODAY = LocalDate(2024, 1, 15)
private val WORKSPACE = workspaceId("ws-1")
private val GROCERIES = categoryId("groceries")

class GetBudgetTransactionsUseCaseTest : StringSpec({

    val budget = aBudget(
        workspaceId = WORKSPACE,
        categoryIds = listOf(GROCERIES),
        period = BudgetPeriod.MONTHLY,
        startDate = LocalDate(2024, 1, 1),
    )

    "asks the repository for the budget's current window, closed at its last day" {
        val env = BudgetTransactionsEnv(emptyList())

        env.useCase(budget)

        // The budget window is half-open [Jan 1, Feb 1); the query one is closed [Jan 1, Jan 31].
        env.requestedWindow shouldBe TransactionPeriodWindow(LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))
        env.requestedAccountId shouldBe null
    }

    "keeps only the rows the budget counts" {
        val kept = aTransaction(id = transactionId("kept"), workspaceId = WORKSPACE, categoryId = GROCERIES)
        val env = BudgetTransactionsEnv(
            listOf(
                kept,
                aTransaction(workspaceId = WORKSPACE, categoryId = categoryId("fuel")),
                aTransaction(workspaceId = WORKSPACE, categoryId = GROCERIES, type = TransactionType.INCOME),
                aTransaction(
                    workspaceId = WORKSPACE,
                    categoryId = GROCERIES,
                    status = TransactionStatus.PLANNED,
                ),
                aTransaction(workspaceId = WORKSPACE, categoryId = GROCERIES, currencyCode = EUR),
                aTransaction(workspaceId = workspaceId("ws-2"), categoryId = GROCERIES),
            ),
        )

        env.useCase(budget).map { it.transaction.id } shouldBe listOf(kept.id)
    }

    "preserves the repository's newest-first order" {
        val rows = listOf("t-3", "t-2", "t-1").map {
            aTransaction(id = transactionId(it), workspaceId = WORKSPACE, categoryId = GROCERIES)
        }
        val env = BudgetTransactionsEnv(rows)

        env.useCase(budget).map { it.transaction.id } shouldBe rows.map { it.id }
    }
})

private class BudgetTransactionsEnv(private val rows: List<Transaction>) {
    var requestedWindow: TransactionPeriodWindow? = null
    var requestedAccountId: AccountId? = null

    private val txRepo = object : TransactionRepository {
        override fun getAll(): Flow<List<Transaction>> = flowOf(rows)
        override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = flowOf(rows)
        override fun getCategorizedWindow(
            accountId: AccountId?,
            window: TransactionPeriodWindow,
            limit: Int,
        ): Flow<List<CategorizedTransaction>> {
            requestedAccountId = accountId
            requestedWindow = window
            return flowOf(rows.map { CategorizedTransaction(it, categoryName = null) })
        }
        override fun getTotals(
            accountId: AccountId?,
            window: TransactionPeriodWindow,
        ): Flow<List<TransactionTotal>> = flowOf(emptyList())
        override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = flowOf(rows)
        override suspend fun getById(id: TransactionId): Transaction? = rows.find { it.id == id }
        override suspend fun insert(transaction: Transaction) = Unit
        override suspend fun update(transaction: Transaction) = Unit
        override suspend fun delete(id: TransactionId) = Unit
    }

    private val workspaceRepo = object : WorkspaceRepository {
        override fun getAll(): Flow<List<Workspace>> = flowOf(emptyList())
        override fun getByUserId(userId: UserId): Flow<List<Workspace>> = flowOf(emptyList())
        override suspend fun getById(id: WorkspaceId): Workspace? = aWorkspace(id = id)
        override suspend fun insert(workspace: Workspace) = Unit
        override suspend fun update(workspace: Workspace) = Unit
        override suspend fun delete(id: WorkspaceId) = Unit
    }

    private val useCase = GetBudgetTransactionsUseCase(
        transactionRepository = txRepo,
        workspaceRepository = workspaceRepo,
        clock = ClockUseCase(FixedClock(TODAY.atStartOfDayIn(TimeZone.UTC))),
    )

    suspend fun useCase(budget: Budget): List<CategorizedTransaction> = useCase(budget, TimeZone.UTC).first()
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
