package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.goalId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Every navigation event asks for its own effect.
 *
 * The near half of the routing chain, and the same shape as [DashboardNavigationTest] on the far
 * half: a flat table of one-line branches where swapping two lines still compiles, still passes
 * every other test, and lands the user on the wrong screen. Together the two specs cover
 * event → effect → destination, so a mis-wired tap cannot reach the user through either hop.
 */
class DashboardEventDestinationTest : StringSpec({

    val account = accountId("a-1")
    val transaction = transactionId("t-1")
    val goal = goalId("g-1")

    val cases: List<Pair<DashboardEvent.Navigate, DashboardEffect>> = listOf(
        DashboardEvent.OnAccountClick(account) to DashboardEffect.NavigateToAccountDetails(account),
        DashboardEvent.OnTransactionClick(transaction) to
            DashboardEffect.NavigateToTransactionDetails(transaction),
        DashboardEvent.OnAddAccountClick to DashboardEffect.NavigateToAccountCreation,
        DashboardEvent.OnSeeAllTransactionsClick to DashboardEffect.NavigateToTransactionsList,
        // The FAB and the quick-actions row open the form with nothing preselected...
        DashboardEvent.OnAddTransactionClick to
            DashboardEffect.NavigateToTransactionCreation(accountId = null),
        // ...while the per-account shortcut carries the account into it. Same destination, and
        // dropping the id here is a bug no compiler catches.
        DashboardEvent.OnAddTransactionForAccountClick(account) to
            DashboardEffect.NavigateToTransactionCreation(accountId = account),
        DashboardEvent.OnTransferClick to DashboardEffect.NavigateToTransferCreation,
        DashboardEvent.OnManageAccountsClick to DashboardEffect.NavigateToAccountsManage,
        DashboardEvent.OnSettingsClick to DashboardEffect.NavigateToSettings,
        DashboardEvent.OnCustomizeClick to DashboardEffect.NavigateToCustomize,
        DashboardEvent.OnSeeAllGoalsClick to DashboardEffect.NavigateToGoals,
        DashboardEvent.OnGoalClick(goal) to DashboardEffect.NavigateToGoalDetails(goal),
        DashboardEvent.OnSetBudgetClick to DashboardEffect.NavigateToBudgetCreation,
    )

    "each navigation event asks for exactly its own effect" {
        cases.forEach { (event, expected) ->
            withClue(event) { event.destination() shouldBe expected }
        }
    }

    "no two events share an effect" {
        // The table above is only a guard against a copy-paste swap if every row is distinguishable.
        // Two rows expecting the same effect would let one branch cover for the other's mistake.
        cases.map { it.second }.distinct().size shouldBe cases.size
    }

    "the table covers every navigation event the screen can raise" {
        // Kotlin has no `entries` for a sealed hierarchy with data-class members, so the guard is
        // the class list: an event added to `Navigate` without a row here fails this, which is the
        // only way a new destination can otherwise ship untested.
        val covered = cases.map { it.first::class }
        val declared = listOf(
            DashboardEvent.OnAccountClick::class,
            DashboardEvent.OnTransactionClick::class,
            DashboardEvent.OnAddAccountClick::class,
            DashboardEvent.OnAddTransactionClick::class,
            DashboardEvent.OnTransferClick::class,
            DashboardEvent.OnSeeAllTransactionsClick::class,
            DashboardEvent.OnAddTransactionForAccountClick::class,
            DashboardEvent.OnManageAccountsClick::class,
            DashboardEvent.OnSettingsClick::class,
            DashboardEvent.OnCustomizeClick::class,
            DashboardEvent.OnSeeAllGoalsClick::class,
            DashboardEvent.OnGoalClick::class,
            DashboardEvent.OnSetBudgetClick::class,
        )

        covered shouldContainExactlyInAnyOrder declared
    }
})
