package com.georgeci.moneysurfer.feature.budget

import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import moneysurfer.feature.budget.generated.resources.Res
import moneysurfer.feature.budget.generated.resources.budget_period_monthly
import moneysurfer.feature.budget.generated.resources.budget_period_weekly
import moneysurfer.feature.budget.generated.resources.budget_period_yearly
import moneysurfer.feature.budget.generated.resources.budget_status_ok
import moneysurfer.feature.budget.generated.resources.budget_status_over
import moneysurfer.feature.budget.generated.resources.budget_status_warn
import org.jetbrains.compose.resources.stringResource

@Composable
fun budgetPeriodLabel(period: BudgetPeriod): String = stringResource(
    when (period) {
        BudgetPeriod.WEEKLY -> Res.string.budget_period_weekly
        BudgetPeriod.MONTHLY -> Res.string.budget_period_monthly
        BudgetPeriod.YEARLY -> Res.string.budget_period_yearly
    },
)

@Composable
fun budgetStatusLabel(status: BudgetStatus): String = stringResource(
    when (status) {
        BudgetStatus.OK -> Res.string.budget_status_ok
        BudgetStatus.WARN -> Res.string.budget_status_warn
        BudgetStatus.OVER -> Res.string.budget_status_over
    },
)
