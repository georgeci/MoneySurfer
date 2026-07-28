package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.BudgetProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single

/**
 * Live progress for every budget the user is still tracking, empty while nothing backs one — no
 * workspace, no budgets, or a workspace whose budgets were all archived.
 *
 * Archived budgets are dropped before progress is computed rather than after: progress reads the
 * whole transaction list per budget, and a workspace can accumulate archived budgets indefinitely.
 *
 * The workspace comes from the budgets themselves rather than from a second subscription to
 * `SessionPointers.currentWorkspaceId`. Two collectors of the same pointer are not ordered against
 * each other, so on a workspace switch the budget query can deliver the new workspace's budgets
 * while the separately-read id is still the old one — and `progressOf` would then match those
 * budgets against the previous workspace's transactions, briefly reporting the full limit as
 * unspent. Reading both from one emission makes that pairing impossible; [GetBudgetsUseCase]
 * already returns an empty list when nobody is signed in, which is the same "nothing to show"
 * answer.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetActiveBudgetProgressUseCase(
    private val getBudgets: GetBudgetsUseCase,
    private val getBudgetProgress: GetBudgetProgressUseCase,
) {

    operator fun invoke(
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<List<BudgetProgress>> =
        getBudgets()
            .map { budgets -> budgets.filter { it.isActive } }
            .distinctUntilChanged()
            .flatMapLatest { budgets ->
                val workspaceId = budgets.firstOrNull()?.workspaceId
                if (workspaceId == null) {
                    flowOf(emptyList())
                } else {
                    getBudgetProgress.progressOf(workspaceId, budgets, timeZone)
                }
            }
}
