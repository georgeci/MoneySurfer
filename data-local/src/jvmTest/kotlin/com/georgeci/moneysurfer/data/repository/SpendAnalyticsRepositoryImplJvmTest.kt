package com.georgeci.moneysurfer.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.CurrencyTotal
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * The canonical spend predicate, one spec per rule, against the real bundled SQLite.
 *
 * Worth a database test rather than a unit one because every guarantee here lives in SQL — the
 * `transferId IS NULL` term, the currency filter and the bucket it feeds, the half-open date
 * bounds over ISO text, and `GROUP BY` over a nullable `categoryId`. A fake DAO would reproduce
 * whatever the author believed the query said.
 */
class SpendAnalyticsRepositoryImplJvmTest : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var repository: SpendAnalyticsRepositoryImpl
    lateinit var categoryTrend: CategorySpendRepositoryImpl

    suspend fun insert(vararg rows: TransactionEntity) =
        database.transactionDao().insertAll(rows.toList())

    beforeEach {
        database = inMemoryDatabase()
        repository = SpendAnalyticsRepositoryImpl(database.transactionDao())
        categoryTrend = CategorySpendRepositoryImpl(database.transactionDao())
        database.seedWorkspace()
    }

    afterEach { database.close() }

    "an expense lands in its category bucket, as a positive magnitude" {
        insert(row(id = "lunch", date = "2025-03-10", amount = -12_00L, categoryId = FOOD))

        repository.byCategory(MARCH).first() shouldContainExactly listOf(
            slice(FOOD, 12_00L, count = 1),
        )
    }

    "both legs of a transfer contribute zero, category or not" {
        insert(
            row(id = "leg-out", date = "2025-03-10", amount = -50_00L, categoryId = MOVES, transferId = "tr-1"),
            row(id = "leg-in", date = "2025-03-10", amount = 50_00L, categoryId = MOVES, transferId = "tr-1"),
        )

        repository.byCategory(MARCH).first().shouldBeEmpty()
        repository.netByMonth(MARCH).first().shouldBeEmpty()
        repository.daily(MARCH).first().shouldBeEmpty()
    }

    "a foreign-currency expense is absent from the categories and reported as excluded" {
        insert(
            row(id = "usd", date = "2025-03-10", amount = -20_00L, categoryId = FOOD),
            row(id = "eur", date = "2025-03-11", amount = -30_00L, categoryId = FOOD, currency = "EUR"),
        )

        repository.byCategory(MARCH).first() shouldContainExactly listOf(slice(FOOD, 20_00L, count = 1))
        repository.excludedByCurrency(MARCH).first() shouldContainExactly listOf(
            CurrencyTotal(CurrencyCode("EUR"), Money.fromMinor(30_00L)),
        )
    }

    "a base-currency-only window reports nothing as excluded" {
        insert(row(id = "usd", date = "2025-03-10", amount = -20_00L, categoryId = FOOD))

        repository.excludedByCurrency(MARCH).first().shouldBeEmpty()
    }

    "a PLANNED row is not spend that happened" {
        insert(
            row(id = "booked", date = "2025-03-10", amount = -10_00L, categoryId = FOOD),
            row(id = "pencilled-in", date = "2025-03-11", amount = -99_00L, categoryId = FOOD, status = "PLANNED"),
        )

        repository.byCategory(MARCH).first() shouldContainExactly listOf(slice(FOOD, 10_00L, count = 1))
    }

    "a row with no category lands in the uncategorized bucket rather than vanishing" {
        insert(
            row(id = "known", date = "2025-03-10", amount = -10_00L, categoryId = FOOD),
            row(id = "unknown", date = "2025-03-11", amount = -40_00L, categoryId = null),
        )

        repository.byCategory(MARCH).first() shouldContainExactly listOf(
            slice(null, 40_00L, count = 1),
            slice(FOOD, 10_00L, count = 1),
        )
    }

    "the window is inclusive at the start and exclusive at the end" {
        insert(
            row(id = "before", date = "2025-02-28", amount = -1_00L, categoryId = FOOD),
            row(id = "first", date = "2025-03-01", amount = -2_00L, categoryId = FOOD),
            row(id = "last", date = "2025-03-31", amount = -4_00L, categoryId = FOOD),
            row(id = "after", date = "2025-04-01", amount = -8_00L, categoryId = FOOD),
        )

        repository.byCategory(MARCH).first() shouldContainExactly listOf(slice(FOOD, 6_00L, count = 2))
    }

    "an unbounded window spans every month" {
        insert(
            row(id = "old", date = "2024-11-30", amount = -1_00L, categoryId = FOOD),
            row(id = "new", date = "2025-04-01", amount = -2_00L, categoryId = FOOD),
        )

        repository.byCategory(ALL_TIME).first() shouldContainExactly listOf(slice(FOOD, 3_00L, count = 2))
    }

    /**
     * The `operationDate = date(operationDate)` term. Every spelling here is one `LocalDate.parse`
     * would also refuse, so without it the category and merchant rollups (which never parse) would
     * count these rows while the month and day series (which do) dropped them — the two would then
     * disagree by exactly this amount. A bounded window hides some of it by string comparison, so
     * the assertions run over both.
     */
    listOf(
        "" to "never written, from before the column existed",
        "2025-3-5" to "unpadded",
        "2025-03-05T10:00:00" to "a timestamp, not a date",
        "2025-02-30" to "a day that does not exist",
    ).forEach { (stored, why) ->
        "an operationDate that is $why is counted by no aggregate" {
            insert(row(id = "odd", date = stored, amount = -10_00L, categoryId = FOOD, merchant = "Aldi"))

            listOf(ALL_TIME, MARCH).forEach { scope ->
                repository.byCategory(scope).first().shouldBeEmpty()
                repository.netByMonth(scope).first().shouldBeEmpty()
                repository.daily(scope).first().shouldBeEmpty()
                repository.topMerchants(scope, limit = 10).first().shouldBeEmpty()
            }
        }
    }

    "a canonical operationDate on the same day still counts" {
        insert(row(id = "fine", date = "2025-03-05", amount = -10_00L, categoryId = FOOD))

        repository.byCategory(MARCH).first() shouldContainExactly listOf(slice(FOOD, 10_00L, count = 1))
    }

    "income counts in the monthly net and nowhere else" {
        insert(
            row(id = "salary", date = "2025-03-01", amount = 300_00L, type = "INCOME", categoryId = null),
            row(id = "rent", date = "2025-03-02", amount = -100_00L, categoryId = FOOD),
            row(id = "opening", date = "2025-03-03", amount = 500_00L, type = "OPENING_BALANCE", categoryId = null),
        )

        repository.netByMonth(MARCH).first() shouldContainExactly listOf(
            monthlyNet("2025-03", income = 300_00L, expense = 100_00L),
        )
        repository.byCategory(MARCH).first() shouldContainExactly listOf(slice(FOOD, 100_00L, count = 1))
    }

    "the monthly net comes back oldest first, with the missing side folded to zero" {
        insert(
            row(id = "feb-spend", date = "2025-02-10", amount = -10_00L, categoryId = FOOD),
            row(id = "mar-earn", date = "2025-03-10", amount = 20_00L, type = "INCOME", categoryId = null),
        )

        repository.netByMonth(ALL_TIME).first() shouldContainExactly listOf(
            monthlyNet("2025-02", income = 0L, expense = 10_00L),
            monthlyNet("2025-03", income = 20_00L, expense = 0L),
        )
    }

    "an opening balance does not conjure a month into the trend" {
        insert(row(id = "opening", date = "2025-03-01", amount = 500_00L, type = "OPENING_BALANCE", categoryId = null))

        repository.netByMonth(MARCH).first().shouldBeEmpty()
    }

    "the net of a month is its income less its expense" {
        insert(
            row(id = "salary", date = "2025-03-01", amount = 300_00L, type = "INCOME", categoryId = null),
            row(id = "rent", date = "2025-03-02", amount = -120_00L, categoryId = FOOD),
        )

        repository.netByMonth(MARCH).first().single().net shouldBe Money.fromMinor(180_00L)
    }

    "the daily series sums a day's rows and skips the days without any" {
        insert(
            row(id = "coffee", date = "2025-03-10", amount = -3_00L, categoryId = FOOD),
            row(id = "lunch", date = "2025-03-10", amount = -12_00L, categoryId = FOOD),
            row(id = "cinema", date = "2025-03-12", amount = -20_00L, categoryId = FOOD),
        )

        repository.daily(MARCH).first().map { it.date to it.total.minor } shouldContainExactly listOf(
            LocalDate(2025, 3, 10) to 15_00L,
            LocalDate(2025, 3, 12) to 20_00L,
        )
    }

    "merchants come back biggest first, bounded by the limit" {
        insert(
            row(id = "a", date = "2025-03-01", amount = -10_00L, categoryId = FOOD, merchant = "Aldi"),
            row(id = "b", date = "2025-03-02", amount = -30_00L, categoryId = FOOD, merchant = "Lidl"),
            row(id = "c", date = "2025-03-03", amount = -5_00L, categoryId = FOOD, merchant = "Lidl"),
            row(id = "d", date = "2025-03-04", amount = -1_00L, categoryId = FOOD, merchant = "Rewe"),
        )

        repository.topMerchants(MARCH, limit = 2).first()
            .map { Triple(it.merchant, it.total.minor, it.transactionCount) } shouldContainExactly listOf(
            Triple("Lidl", 35_00L, 2),
            Triple("Aldi", 10_00L, 1),
        )
    }

    "a row with no merchant is not a merchant" {
        insert(
            row(id = "anonymous", date = "2025-03-01", amount = -99_00L, categoryId = FOOD),
            row(id = "named", date = "2025-03-02", amount = -1_00L, categoryId = FOOD, merchant = "Aldi"),
        )

        repository.topMerchants(MARCH, limit = 10).first().map { it.merchant } shouldContainExactly listOf("Aldi")
    }

    /**
     * The alignment half of this change: the shipped category trend used to sum transfer legs and
     * add foreign minor units at face value, so it disagreed with the budget screen for the same
     * month. Same predicate now, same answer.
     */
    "the category trend counts neither transfer legs nor foreign currency" {
        insert(
            row(id = "lunch", date = "2025-03-10", amount = -12_00L, categoryId = FOOD),
            row(id = "leg", date = "2025-03-11", amount = -50_00L, categoryId = FOOD, transferId = "tr-1"),
            row(id = "abroad", date = "2025-03-12", amount = -30_00L, categoryId = FOOD, currency = "EUR"),
        )

        categoryTrend.monthlyTotals(
            workspaceId = WORKSPACE,
            categoryIds = listOf(FOOD),
            type = TransactionType.EXPENSE,
            baseCurrency = USD,
            fromMonth = YearMonth(2025, 3),
            toMonth = YearMonth(2025, 3),
        ).first().map { it.total.minor } shouldContainExactly listOf(12_00L)
    }

    "a workspace whose base currency could not be read reports no trend rather than a wrong one" {
        insert(row(id = "lunch", date = "2025-03-10", amount = -12_00L, categoryId = FOOD))

        categoryTrend.monthlyTotals(
            workspaceId = WORKSPACE,
            categoryIds = listOf(FOOD),
            type = TransactionType.EXPENSE,
            baseCurrency = null,
            fromMonth = YearMonth(2025, 3),
            toMonth = YearMonth(2025, 3),
        ).first().shouldBeEmpty()
    }
})

private const val OWNER_ID = "owner-1"
private const val WORKSPACE_ID = "ws-1"
private const val ACCOUNT_ID = "acc-1"
private const val FIXTURE_TIME = 1_700_000_000_000L

private val WORKSPACE = WorkspaceId(WORKSPACE_ID)
private val USD = CurrencyCode("USD")
private val FOOD = CategoryId("cat-food")
private val MOVES = CategoryId("cat-transfer")

private val MARCH = SpendScope(
    workspaceId = WORKSPACE,
    baseCurrency = USD,
    window = TransactionPeriodWindow(LocalDate(2025, 3, 1), LocalDate(2025, 3, 31)),
)

private val ALL_TIME = MARCH.copy(window = TransactionPeriodWindow.Unbounded)

/** Mirrors the on-disk JVM builder so behaviour matches production except for persistence. */
private fun inMemoryDatabase(): MoneySurferDatabase =
    Room.inMemoryDatabaseBuilder<MoneySurferDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** Foreign keys are enforced, so the workspace, account and categories have to exist first. */
private suspend fun MoneySurferDatabase.seedWorkspace() {
    userDao().insert(UserEntity(id = OWNER_ID, displayName = "Owner", isAnon = false))
    workspaceDao().insert(
        WorkspaceEntity(
            id = WORKSPACE_ID,
            name = "WS",
            description = "",
            baseCurrency = USD.value,
            ownerId = OWNER_ID,
            createdAt = FIXTURE_TIME,
            archived = false,
        ),
    )
    accountDao().insert(
        AccountEntity(
            id = ACCOUNT_ID,
            workspaceId = WORKSPACE_ID,
            name = "Cash",
            type = "CASH",
            currency = USD.value,
            balance = 0L,
        ),
    )
    listOf(FOOD to "EXPENSE", MOVES to "TRANSFER").forEach { (id, type) ->
        categoryDao().insert(
            CategoryEntity(
                id = id.value,
                workspaceId = WORKSPACE_ID,
                name = id.value,
                type = type,
                parentId = null,
                createdAt = FIXTURE_TIME,
            ),
        )
    }
}

@Suppress("LongParameterList")
private fun row(
    id: String,
    date: String,
    amount: Long,
    categoryId: CategoryId?,
    type: String = "EXPENSE",
    status: String = "ACTUAL",
    currency: String = "USD",
    merchant: String = "",
    transferId: String? = null,
) = TransactionEntity(
    id = id,
    workspaceId = WORKSPACE_ID,
    accountId = ACCOUNT_ID,
    amount = amount,
    currencyCode = currency,
    categoryId = categoryId?.value,
    note = id,
    merchant = merchant,
    operationAt = FIXTURE_TIME,
    operationDate = date,
    type = type,
    status = status,
    createdAt = FIXTURE_TIME,
    updatedAt = FIXTURE_TIME,
    transferId = transferId,
)

private fun slice(categoryId: CategoryId?, minor: Long, count: Int) =
    CategorySpendSlice(
        categoryId = categoryId,
        total = Money.fromMinor(minor),
        transactionCount = count,
    )

private fun monthlyNet(month: String, income: Long, expense: Long) =
    MonthlyNet(
        month = YearMonth.parse(month),
        income = Money.fromMinor(income),
        expense = Money.fromMinor(expense),
    )
