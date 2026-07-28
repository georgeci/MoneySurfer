package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.CurrencyTotal
import com.georgeci.moneysurfer.domain.model.DailySpendPoint
import com.georgeci.moneysurfer.domain.model.MerchantSpend
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.model.SpendScope
import kotlinx.coroutines.flow.Flow

/**
 * Read-only spend rollups over transactions, all answered by one `GROUP BY` in SQLite.
 *
 * Aggregation-only on purpose, following the [CategorySpendRepository] precedent: nothing here
 * hands back a transaction, so the amount of data crossing the boundary is tens of rows whatever
 * the workspace holds. Insights code must never reach for `TransactionRepository.getByWorkspaceId`
 * and fold — that is the pattern this interface exists to replace.
 *
 * ## What counts as spend
 *
 * Every query below shares one predicate, the one budgets already apply
 * ([com.georgeci.moneysurfer.domain.model.Budget.counts]):
 *
 * - `type = EXPENSE` — income is not spend, and an opening balance is an account artefact.
 * - `status = ACTUAL` — a planned transaction is not spend that happened.
 * - `transferId IS NULL` — moving money between the user's own accounts is not spending. The legs
 *   *do* carry a category (`CreateTransferUseCase` assigns a `CategoryType.TRANSFER` one), so
 *   filtering by category type would not catch them.
 * - `currencyCode = ` [SpendScope.baseCurrency] — see below.
 * - `operationDate` inside [SpendScope.window] — the business date the user assigned the row to,
 *   the same date budgets count it under, never a zone-dependent re-derivation of `operationAt`.
 *
 * [netByMonth] is the single exception: it needs income too, so it widens the type term to
 * `EXPENSE or INCOME` — still excluding opening balances — and keeps every other term.
 *
 * ## Why v1 converts nothing
 *
 * A historical series stays in the base currency. Only the latest FX table is cached
 * (`replaceForBase` wipes the previous one) and `Transaction` carries no rate snapshot, so
 * converting a twelve-month trend would silently reshape history every time a rate moved. What
 * the filter left out is reported by [excludedByCurrency] rather than dropped — the same contract
 * [com.georgeci.moneysurfer.domain.model.ConvertedTotal.unconverted] already uses. Balances still
 * convert at today's rate, because a balance is a present-day quantity.
 *
 * ## Collecting these flows
 *
 * Room's invalidation tracker fires per write, so a sync pull applying thousands of rows re-runs
 * every aggregate below once per batch. The implementation conflates for that reason; a collector
 * that adds its own buffering should not undo it.
 */
interface SpendAnalyticsRepository {

    /**
     * Spend per category inside the window, largest first.
     *
     * A row with no category lands in the [CategorySpendSlice.categoryId] `null` bucket rather
     * than being skipped, so the slices still add up to the window's total spend.
     */
    fun byCategory(scope: SpendScope): Flow<List<CategorySpendSlice>>

    /**
     * Income and expense per calendar month inside the window, oldest first.
     *
     * The only query that looks past expenses. Months the workspace booked nothing in are absent
     * — a chart that wants a fixed number of columns fills the gaps itself, the way
     * `buildCategorySpendHistory` already does for the category trend.
     */
    fun netByMonth(scope: SpendScope): Flow<List<MonthlyNet>>

    /** Spend per calendar day inside the window, oldest first. Days without spend are absent. */
    fun daily(scope: SpendScope): Flow<List<DailySpendPoint>>

    /**
     * The [limit] merchants that took the most inside the window, largest first.
     *
     * Rows with no merchant are excluded: unlike a missing category, a blank merchant is not a
     * counterparty the user could recognise, and it would otherwise be the biggest "merchant" in
     * most workspaces.
     */
    fun topMerchants(scope: SpendScope, limit: Int): Flow<List<MerchantSpend>>

    /**
     * Per-currency spend the [SpendScope.baseCurrency] filter left out, largest first — empty when
     * the window holds nothing but base-currency expenses.
     *
     * The invariant this preserves: an expense that clears every *other* term of the predicate is
     * either inside the four results above or listed here, never neither. A number the UI cannot
     * complete is a display problem, not a licence to drop money.
     */
    fun excludedByCurrency(scope: SpendScope): Flow<List<CurrencyTotal>>
}
