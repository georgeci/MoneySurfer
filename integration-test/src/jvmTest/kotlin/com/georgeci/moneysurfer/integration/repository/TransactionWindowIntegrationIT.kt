package com.georgeci.moneysurfer.integration.repository

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_27_28
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.periodWindow
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * The windowed transactions queries against the real bundled SQLite: the date bounds, the paging
 * limit, the opening-balance exclusion, and the per-currency totals aggregation the list summary
 * is built from.
 *
 * Worth an integration test rather than a unit one because every guarantee here lives in SQL —
 * lexicographic comparison of ISO date text, `SUM(ABS(...))` grouping, and the legacy type
 * spellings — none of which a fake repository would reproduce.
 */
class TransactionWindowIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: FinanceStack

    suspend fun rowsIn(window: TransactionPeriodWindow, limit: Int = 100): List<String> =
        stack.transactionRepository
            .getCategorizedWindow(accountId = ACCOUNT, window = window, limit = limit)
            .first()
            .map { it.transaction.id.value }

    suspend fun totalsIn(window: TransactionPeriodWindow): List<TransactionTotal> =
        stack.transactionRepository.getTotals(accountId = ACCOUNT, window = window).first()

    beforeEach {
        harness = IntegrationHarness()
        stack = FinanceStack(harness)
        harness.seedWorkspace()
        harness.database.accountDao().insert(
            AccountEntity(
                id = ACCOUNT_ID,
                workspaceId = WORKSPACE_ID_VALUE,
                name = "Cash",
                type = "CASH",
                currency = "USD",
                balance = 0L,
            ),
        )
    }

    afterEach { harness.close() }

    "a month window keeps the boundary days and drops the neighbours" {
        harness.database.transactionDao().insertAll(
            listOf(
                row(id = "before", date = "2025-02-28"),
                row(id = "first", date = "2025-03-01"),
                row(id = "last", date = "2025-03-31"),
                row(id = "after", date = "2025-04-01"),
            ),
        )

        val march = periodWindow(TransactionPeriodMode.Month, LocalDate(2025, 3, 15))

        rowsIn(march) shouldContainExactlyInAnyOrder listOf("first", "last")
    }

    "rows come back newest first" {
        harness.database.transactionDao().insertAll(
            listOf(
                row(id = "mid", date = "2025-03-10"),
                row(id = "newest", date = "2025-03-20"),
                row(id = "oldest", date = "2025-03-01"),
            ),
        )

        rowsIn(TransactionPeriodWindow.Unbounded) shouldContainExactly listOf("newest", "mid", "oldest")
    }

    "the limit bounds an all-time query" {
        harness.database.transactionDao().insertAll(
            (1..10).map { row(id = "t-$it", date = "2025-03-%02d".format(it)) },
        )

        rowsIn(TransactionPeriodWindow.Unbounded, limit = 4).size shouldBe 4
    }

    "opening balances never appear in the list, under either spelling" {
        harness.database.transactionDao().insertAll(
            listOf(
                row(id = "spend", date = "2025-03-10"),
                row(id = "opening", date = "2025-03-10", type = "OPENING_BALANCE"),
                row(id = "legacy-opening", date = "2025-03-10", type = "INITIAL_BALANCE"),
            ),
        )

        rowsIn(TransactionPeriodWindow.Unbounded) shouldContainExactly listOf("spend")
    }

    "totals sum magnitudes per type over the whole window, not per page" {
        harness.database.transactionDao().insertAll(
            listOf(
                row(id = "salary", date = "2025-03-01", amount = 300_00L, type = "INCOME"),
                row(id = "rent", date = "2025-03-02", amount = -100_00L),
                row(id = "food", date = "2025-03-03", amount = -20_00L),
                row(id = "old", date = "2025-01-03", amount = -999_00L),
            ),
        )

        val march = periodWindow(TransactionPeriodMode.Month, LocalDate(2025, 3, 15))

        totalsIn(march) shouldContainExactlyInAnyOrder listOf(
            TransactionTotal(TransactionType.INCOME, USD, Money.fromMinor(300_00L)),
            TransactionTotal(TransactionType.EXPENSE, USD, Money.fromMinor(120_00L)),
        )
    }

    "totals stay split by currency rather than adding minor units together" {
        harness.database.transactionDao().insertAll(
            listOf(
                row(id = "usd", date = "2025-03-01", amount = -10_00L),
                row(id = "eur", date = "2025-03-02", amount = -30_00L, currency = "EUR"),
            ),
        )

        totalsIn(TransactionPeriodWindow.Unbounded) shouldContainExactlyInAnyOrder listOf(
            TransactionTotal(TransactionType.EXPENSE, USD, Money.fromMinor(10_00L)),
            TransactionTotal(TransactionType.EXPENSE, CurrencyCode("EUR"), Money.fromMinor(30_00L)),
        )
    }

    "the legacy REGULAR type is bucketed by the amount's sign" {
        harness.database.transactionDao().insertAll(
            listOf(
                row(id = "legacy-in", date = "2025-03-01", amount = 50_00L, type = "REGULAR"),
                row(id = "legacy-out", date = "2025-03-02", amount = -20_00L, type = "REGULAR"),
            ),
        )

        totalsIn(TransactionPeriodWindow.Unbounded) shouldContainExactlyInAnyOrder listOf(
            TransactionTotal(TransactionType.INCOME, USD, Money.fromMinor(50_00L)),
            TransactionTotal(TransactionType.EXPENSE, USD, Money.fromMinor(20_00L)),
        )
    }

    /**
     * `splitLegCount` is a correlated subquery, and the two things it must not do are count a
     * non-split row (`splitId = NULL` matches nothing in SQL, so the count is 0) and leak across
     * groups. Both are invisible to a fake repository, which is why they are pinned against real
     * SQLite: the list decides whether to collapse a group from this number alone.
     */
    "the window reports each row's split group size, and zero for rows outside a split" {
        harness.database.transactionDao().insertAll(
            listOf(
                row(id = "leg-a", date = "2025-03-10", splitId = "sp-1"),
                row(id = "leg-b", date = "2025-03-10", splitId = "sp-1"),
                row(id = "leg-c", date = "2025-03-10", splitId = "sp-1"),
                row(id = "other-leg", date = "2025-03-11", splitId = "sp-2"),
                row(id = "plain", date = "2025-03-12"),
            ),
        )

        val counts = stack.transactionRepository
            .getCategorizedWindow(accountId = ACCOUNT, window = TransactionPeriodWindow.Unbounded, limit = 100)
            .first()
            .associate { it.transaction.id.value to it.splitLegCount }

        counts shouldBe mapOf(
            "leg-a" to 3,
            "leg-b" to 3,
            "leg-c" to 3,
            "other-leg" to 1,
            "plain" to 0,
        )
    }

    "getBySplitId returns only the legs of the requested receipt" {
        harness.database.transactionDao().insertAll(
            listOf(
                row(id = "leg-a", date = "2025-03-10", splitId = "sp-1"),
                row(id = "leg-b", date = "2025-03-10", splitId = "sp-1"),
                row(id = "other-leg", date = "2025-03-10", splitId = "sp-2"),
                row(id = "plain", date = "2025-03-10"),
            ),
        )

        stack.transactionRepository.getBySplitId(SplitId("sp-1")).map { it.id.value } shouldBe
            listOf("leg-a", "leg-b")
    }

    /**
     * The reason `MIGRATION_27_28` exists: a row whose `operationDate` was never written compares
     * false against every bound, so it would silently vanish from every date window. Simulated
     * here by writing the empty column value the old schema left behind.
     */
    "a row with no operationDate is invisible to a windowed query" {
        harness.database.transactionDao().insertAll(
            listOf(row(id = "legacy", date = "", amount = -10_00L)),
        )

        val march = periodWindow(TransactionPeriodMode.Month, LocalDate(2025, 3, 15))

        rowsIn(march).shouldContainExactly(emptyList())
        rowsIn(TransactionPeriodWindow.Unbounded) shouldContainExactly listOf("legacy")
    }

    /**
     * …and the backfill that rescues it. Run against a bare SQLite connection rather than the Room
     * handle: Room only exposes a migration path when it is actually upgrading a v27 file, and the
     * only thing worth pinning here is that `date(unixepoch)` reproduces the UTC date
     * `resolveLegacyOperationDate` has been deriving for these rows all along.
     */
    "the v27 to v28 backfill derives the same UTC date the readers already do" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execSQL(
                "CREATE TABLE `transactions` (`id` TEXT, `operationAt` INTEGER, `operationDate` TEXT)",
            )
            // 2025-03-15T12:00:00Z, plus a row already carrying a date the migration must not touch.
            connection.execSQL(
                "INSERT INTO `transactions` VALUES ('legacy', 1741953600000, ''), " +
                    "('kept', 1741953600000, '2020-01-01')",
            )

            MIGRATION_27_28.migrate(connection)

            connection.storedOperationDates() shouldBe mapOf(
                "legacy" to "2025-03-14",
                "kept" to "2020-01-01",
            )
        } finally {
            connection.close()
        }
    }
})

private fun SQLiteConnection.storedOperationDates(): Map<String, String> = buildMap {
    prepare("SELECT `id`, `operationDate` FROM `transactions`").use { statement ->
        while (statement.step()) put(statement.getText(0), statement.getText(1))
    }
}

private const val ACCOUNT_ID = "acc-1"
private val ACCOUNT = AccountId(ACCOUNT_ID)
private val USD = CurrencyCode("USD")

private fun row(
    id: String,
    date: String,
    amount: Long = -10_00L,
    type: String = "EXPENSE",
    currency: String = "USD",
    splitId: String? = null,
) = TransactionEntity(
    id = id,
    workspaceId = WORKSPACE_ID_VALUE,
    accountId = ACCOUNT_ID,
    amount = amount,
    currencyCode = currency,
    categoryId = null,
    note = id,
    merchant = "",
    operationAt = FIXTURE_TIME,
    operationDate = date,
    type = type,
    createdAt = FIXTURE_TIME,
    updatedAt = FIXTURE_TIME,
    splitId = splitId,
)
