package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.CategoryMonthlyTotal
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

/**
 * Read-only aggregates over transactions grouped by category and month.
 *
 * Separate from [TransactionRepository] on purpose: that interface hands back whole transactions,
 * and a six-month trend over a busy category would mean loading every row just to sum it in
 * memory on every emission. This one is answered by a `GROUP BY` in SQLite, so the amount of
 * data crossing the boundary is one row per category per month regardless of volume.
 */
interface CategorySpendRepository {

    /**
     * Per-month totals for [categoryIds], over the months [fromMonth]..[toMonth] inclusive.
     *
     * Only `ACTUAL` transactions of [type] count — a planned transaction is not spend that
     * happened, and mixing income into an expense trend would net the two out to nonsense.
     * Months a category booked nothing in are simply absent from the result.
     */
    fun monthlyTotals(
        workspaceId: WorkspaceId,
        categoryIds: List<CategoryId>,
        type: TransactionType,
        fromMonth: YearMonth,
        toMonth: YearMonth,
    ): Flow<List<CategoryMonthlyTotal>>
}
