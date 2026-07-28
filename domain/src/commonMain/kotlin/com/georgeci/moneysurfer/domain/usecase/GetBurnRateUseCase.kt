package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.BurnRate
import com.georgeci.moneysurfer.domain.model.monthlySpendCap
import com.georgeci.moneysurfer.domain.model.project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single

/**
 * Live burn rate for the active workspace: the week's spend, and where the month lands if it keeps
 * up. Null only when [GetDailySpendSeriesUseCase] has nothing to read — a missing budget is not a
 * missing burn rate, it just leaves the pace verdict off.
 *
 * The cap is reduced to a single [com.georgeci.moneysurfer.domain.primitives.Money] before it is
 * combined, so the projection is not recomputed when a budget is renamed, restyled or archived
 * without changing what the month is capped at.
 */
@Single
class GetBurnRateUseCase(
    private val getDailySpendSeries: GetDailySpendSeriesUseCase,
    private val getBudgets: GetBudgetsUseCase,
) {

    operator fun invoke(timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<BurnRate?> {
        val monthlyCap = getBudgets().map { it.monthlySpendCap() }.distinctUntilChanged()
        return combine(getDailySpendSeries(timeZone), monthlyCap) { series, cap -> series?.project(cap) }
    }
}
