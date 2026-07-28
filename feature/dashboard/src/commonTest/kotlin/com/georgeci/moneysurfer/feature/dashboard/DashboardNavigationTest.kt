package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.goalId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Every effect reaches its own destination.
 *
 * The dispatch is a flat table of a dozen one-line branches, which is exactly the shape a
 * copy-paste edit silently breaks: swapping two lines still compiles, still passes every other
 * test, and lands the user on the wrong screen. Recording the destination as a string keeps the
 * assertion about routing rather than about `Route`, which this module does not construct.
 */
class DashboardNavigationTest : StringSpec({

    "each effect reaches exactly one destination" {
        val account = accountId("a-1")
        val transaction = transactionId("t-1")
        val goal = goalId("g-1")
        val budget = budgetId("b-1")

        val cases = listOf(
            DashboardEffect.NavigateToAccountDetails(account) to "accountDetails:${account.value}",
            DashboardEffect.NavigateToTransactionDetails(transaction) to "transactionDetails:${transaction.value}",
            DashboardEffect.NavigateToAccountCreation to "accountCreation",
            DashboardEffect.NavigateToAccountsManage to "accountsManage",
            DashboardEffect.NavigateToTransactionCreation(account) to "transactionCreation:${account.value}",
            // The same destination with no account preselected — the FAB and the quick-actions row.
            DashboardEffect.NavigateToTransactionCreation(accountId = null) to "transactionCreation:null",
            DashboardEffect.NavigateToTransferCreation to "transferCreation",
            DashboardEffect.NavigateToSettings to "settings",
            DashboardEffect.NavigateToCustomize to "customize",
            DashboardEffect.NavigateToTransactionsList to "transactionsList",
            DashboardEffect.NavigateToGoals to "goals",
            DashboardEffect.NavigateToGoalDetails(goal) to "goalDetails:${goal.value}",
            DashboardEffect.NavigateToBudgetCreation to "budgetCreation",
            DashboardEffect.NavigateToBudgets to "budgets",
            DashboardEffect.NavigateToBudgetDetails(budget) to "budgetDetails:${budget.value}",
        )

        cases.forEach { (effect, expected) ->
            val visited = mutableListOf<String>()
            recordingNavigation(visited).navigate(effect)

            withClue(effect) { visited shouldBe listOf(expected) }
        }
    }
})

private fun recordingNavigation(visited: MutableList<String>) = DashboardNavigation(
    onNavigateToAccountCreation = { visited += "accountCreation" },
    onNavigateToAccountsManage = { visited += "accountsManage" },
    onNavigateToTransactionCreation = { id -> visited += "transactionCreation:${id?.value}" },
    onNavigateToTransferCreation = { visited += "transferCreation" },
    onNavigateToAccountDetails = { id -> visited += "accountDetails:${id.value}" },
    onNavigateToTransactionDetails = { id -> visited += "transactionDetails:${id.value}" },
    onNavigateToSettings = { visited += "settings" },
    onNavigateToCustomize = { visited += "customize" },
    onNavigateToTransactionsList = { visited += "transactionsList" },
    onNavigateToGoals = { visited += "goals" },
    onNavigateToGoalDetails = { id -> visited += "goalDetails:${id.value}" },
    onNavigateToBudgetCreation = { visited += "budgetCreation" },
    onNavigateToBudgets = { visited += "budgets" },
    onNavigateToBudgetDetails = { id -> visited += "budgetDetails:${id.value}" },
)
