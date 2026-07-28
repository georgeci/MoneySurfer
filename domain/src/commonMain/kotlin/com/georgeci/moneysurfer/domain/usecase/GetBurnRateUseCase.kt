package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.BurnRate
import com.georgeci.moneysurfer.domain.model.monthlySpendCap
import com.georgeci.moneysurfer.domain.model.project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single

/**
 * Live burn rate for the active workspace: the week's spend, and where the month lands if it keeps
 * up. Null only when [GetDailySpendSeriesUseCase] has nothing to read — a missing budget is not a
 * missing burn rate, it just leaves the pace verdict off.
 *
 * The cap is looked up *against the series' own workspace* rather than against whatever
 * [GetBudgetsUseCase] happens to be answering for: the two use cases subscribe to
 * `SessionPointers.currentWorkspaceId` separately, so mid-switch they can disagree about which
 * workspace is current. [com.georgeci.moneysurfer.domain.model.monthlySpendCap] refuses the pairing
 * in that frame instead of judging one workspace's spend by another's limit.
 *
 * [distinctUntilChanged] sits on the result rather than on the cap, so it absorbs both a budget edit
 * that leaves the cap alone — a rename, a restyle, an archived category budget — and a re-query that
 * returns the same days.
 */
@Single
class GetBurnRateUseCase(
    private val getDailySpendSeries: GetDailySpendSeriesUseCase,
    private val getBudgets: GetBudgetsUseCase,
) {

    operator fun invoke(timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<BurnRate?> =
        combine(getDailySpendSeries(timeZone), getBudgets()) { series, budgets ->
            series?.project(budgets.monthlySpendCap(series.workspaceId))
        }.distinctUntilChanged()
}
