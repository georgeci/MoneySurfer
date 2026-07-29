package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.SafeToSpend
import com.georgeci.moneysurfer.domain.model.safeToSpend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single

/**
 * Live safe-to-spend for the active workspace, or null while nothing backs it — no workspace, no
 * active budget, or a workspace whose budgets were all archived.
 *
 * The figure is a projection of the same active-budget progress the budgets widget lists: see
 * [GetActiveBudgetProgressUseCase], which owns the archived-budget filter and the rule that the
 * workspace comes from the budgets themselves. Sharing that source is what keeps the headline and
 * the budget rows from ever disagreeing about a budget.
 *
 * [preferredPeriod] arrives as a flow rather than a value so that changing it re-picks the budget
 * without tearing down the subscription underneath. The progress query reads the workspace's whole
 * transaction list per emission, and re-running that every time the dashboard's Week/Month switch
 * is tapped would be the most expensive query in the app answering a question it already has the
 * data for. It is required rather than defaulted: this figure is about a period, so a caller has
 * to say which one it is answering for — `flowOf(null)` spells "no period on screen, pick the
 * largest cap", which is a claim worth making out loud.
 */
@Single
class GetSafeToSpendUseCase(
    private val getActiveBudgetProgress: GetActiveBudgetProgressUseCase,
) {

    operator fun invoke(
        preferredPeriod: Flow<BudgetPeriod?>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<SafeToSpend?> =
        combine(
            getActiveBudgetProgress(timeZone),
            preferredPeriod,
        ) { progresses, period -> progresses.safeToSpend(period) }
}
