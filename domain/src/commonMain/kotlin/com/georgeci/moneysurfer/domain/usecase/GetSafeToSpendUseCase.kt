package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.SafeToSpend
import com.georgeci.moneysurfer.domain.model.safeToSpend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetSafeToSpendUseCase(
    private val getBudgets: GetBudgetsUseCase,
    private val getBudgetProgress: GetBudgetProgressUseCase,
    private val session: SessionPointers,
) {

    operator fun invoke(timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<SafeToSpend?> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            if (workspaceId == null) {
                flowOf(null)
            } else {
                getBudgets()
                    .map { budgets -> budgets.filter { it.isActive } }
                    .distinctUntilChanged()
                    .flatMapLatest { budgets ->
                        if (budgets.isEmpty()) {
                            flowOf(null)
                        } else {
                            getBudgetProgress.progressOf(workspaceId, budgets, timeZone)
                                .map { progresses -> progresses.safeToSpend() }
                        }
                    }
            }
        }
}
