package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.SafeToSpend
import com.georgeci.moneysurfer.domain.model.safeToSpend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single

/**
 * Live safe-to-spend for the active workspace, or null while nothing backs it — no workspace, no
 * active budget, or a workspace whose budgets were all archived.
 *
 * The figure is a projection of the same active-budget progress the budgets widget lists (see
 * [GetActiveBudgetProgressUseCase], which owns the workspace-pairing and archived-budget rules), so
 * the headline and the list can never disagree about a budget.
 */
@Single
class GetSafeToSpendUseCase(
    private val getActiveBudgetProgress: GetActiveBudgetProgressUseCase,
) {

    operator fun invoke(timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<SafeToSpend?> =
        getActiveBudgetProgress(timeZone).map { progresses -> progresses.safeToSpend() }
}
