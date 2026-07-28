package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.SafeToSpend
import com.georgeci.moneysurfer.domain.model.safeToSpend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single

/**
 * Live safe-to-spend for the active workspace, or null while nothing backs it — no workspace, no
 * active budget, or a workspace whose budgets were all archived.
 *
 * Archived budgets are dropped before progress is computed rather than after: progress reads the
 * whole transaction list per budget, and a workspace can accumulate archived budgets indefinitely.
 *
 * The workspace comes from the budgets themselves rather than from a second subscription to
 * `SessionPointers.currentWorkspaceId`. Two collectors of the same pointer are not ordered against
 * each other, so on a workspace switch the budget query can deliver the new workspace's budgets
 * while the separately-read id is still the old one — and `progressOf` would then match those
 * budgets against the previous workspace's transactions, briefly reporting the full limit as unspent.
 * Reading both from one emission makes that pairing impossible; [GetBudgetsUseCase] already returns
 * an empty list when nobody is signed in, which is the same "nothing backs it" answer.
 *
 * [preferredPeriod] arrives as a flow rather than a value so that changing it re-picks the budget
 * without tearing down the subscription underneath. `progressOf` reads the workspace's whole
 * transaction list per emission, and re-running that every time the dashboard's Week/Month switch
 * is tapped would be the most expensive query in the app answering a question it already has the
 * data for.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetSafeToSpendUseCase(
    private val getBudgets: GetBudgetsUseCase,
    private val getBudgetProgress: GetBudgetProgressUseCase,
) {

    operator fun invoke(
        preferredPeriod: Flow<BudgetPeriod?> = flowOf(null),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<SafeToSpend?> =
        getBudgets()
            .map { budgets -> budgets.filter { it.isActive } }
            .distinctUntilChanged()
            .flatMapLatest { budgets ->
                val workspaceId = budgets.firstOrNull()?.workspaceId
                if (workspaceId == null) {
                    flowOf(null)
                } else {
                    combine(
                        getBudgetProgress.progressOf(workspaceId, budgets, timeZone),
                        preferredPeriod,
                    ) { progresses, period -> progresses.safeToSpend(period) }
                }
            }
}
