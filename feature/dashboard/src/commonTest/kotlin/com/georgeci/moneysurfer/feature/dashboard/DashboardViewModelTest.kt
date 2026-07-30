package com.georgeci.moneysurfer.feature.dashboard

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.FakeSavingsGoalRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.fixtures.aGoal
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.goalId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The dashboard state the screen assembles regardless of which widget reads it: the layout, the
 * recent list, the goals row, navigation, and the host flags.
 *
 * Per-widget behaviour lives in the sibling `Dashboard*ViewModelTest` specs; shared fakes live in
 * `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "recentTransactionsEmpty is true when no transactions are logged" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(emptyList()),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.recentTransactionsEmpty shouldBe true
    }

    "recentTransactionsEmpty is true when only an opening-balance transaction exists" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val opening = aTransaction(
            id = transactionId("tx-open"),
            workspaceId = ws,
            accountId = account.id,
            type = TransactionType.OPENING_BALANCE,
        )
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(listOf(opening)),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.recentTransactionsEmpty shouldBe true
    }

    "recentTransactionsEmpty is false once a real transaction is logged" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val expense = aTransaction(
            id = transactionId("tx-1"),
            workspaceId = ws,
            accountId = account.id,
            type = TransactionType.EXPENSE,
        )
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(listOf(expense)),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.recentTransactionsEmpty shouldBe false
    }

    "the rendered layout comes from the persisted config, not a fixed list" {
        val ws = workspaceId("ws-1")
        val stored = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Goals),
                DashboardLayoutItem(DashboardWidgetType.Balance, enabled = false),
            ),
        )
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            uiPreferences = FakeUiPreferences(dashboardLayout = stored),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.layout.enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Goals,
            // widgets the stored layout never heard of are appended rather than dropped
            DashboardWidgetType.QuickActions,
            DashboardWidgetType.SafeToSpend,
            DashboardWidgetType.BurnRate,
            DashboardWidgetType.Budgets,
            DashboardWidgetType.SpentByCategory,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.CategoriesDonut,
            DashboardWidgetType.Insights,
            DashboardWidgetType.RecentTransactions,
        )
    }

    "the quick-actions Transfer button asks for the transfer form, not the plain creation one" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        viewModel.onEvent(DashboardEvent.OnTransferClick)

        viewModel.sideEffects.effectFlow.first() shouldBe DashboardEffect.NavigateToTransferCreation
    }

    "a build with transfers switched off says so, so the quick-actions row can stand down" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            hostCapabilities = FakeHostCapabilities(isOffline = true, transferEnabled = false),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.transferEnabled shouldBe false
    }

    "an unset layout falls back to the default order" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.layout shouldBe DashboardLayoutConfig.DEFAULT
    }

    "every tap on the root screen maps to its own destination" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(emptyList()),
        )

        val expected = listOf<Pair<DashboardEvent, DashboardEffect>>(
            DashboardEvent.OnAccountClick(account.id) to
                DashboardEffect.NavigateToAccountDetails(account.id),
            DashboardEvent.OnTransactionClick(transactionId("tx-1")) to
                DashboardEffect.NavigateToTransactionDetails(transactionId("tx-1")),
            DashboardEvent.OnAddAccountClick to DashboardEffect.NavigateToAccountCreation,
            DashboardEvent.OnManageAccountsClick to DashboardEffect.NavigateToAccountsManage,
            DashboardEvent.OnSeeAllTransactionsClick to DashboardEffect.NavigateToTransactionsList,
            // The generic "add" has no account context; the per-account one carries it.
            DashboardEvent.OnAddTransactionClick to
                DashboardEffect.NavigateToTransactionCreation(accountId = null),
            DashboardEvent.OnAddTransactionForAccountClick(account.id) to
                DashboardEffect.NavigateToTransactionCreation(accountId = account.id),
            DashboardEvent.OnSettingsClick to DashboardEffect.NavigateToSettings,
            DashboardEvent.OnCustomizeClick to DashboardEffect.NavigateToCustomize,
            DashboardEvent.OnSeeAllGoalsClick to DashboardEffect.NavigateToGoals,
            DashboardEvent.OnGoalClick(goalId("g-1")) to
                DashboardEffect.NavigateToGoalDetails(goalId("g-1")),
            // The way out of the safe-to-spend widget's empty state.
            DashboardEvent.OnSetBudgetClick to DashboardEffect.NavigateToBudgetCreation,
        )

        viewModel.sideEffects.effectFlow.test {
            expected.forEach { (event, effect) ->
                viewModel.onEvent(event)
                awaitItem() shouldBe effect
            }
        }
    }

    "the recent list is capped at five rows" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val logged = (1..8).map {
            aTransaction(
                id = transactionId("tx-$it"),
                workspaceId = ws,
                accountId = account.id,
                type = TransactionType.EXPENSE,
            )
        }
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(logged),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.transactions.map { it.id } shouldContainExactly (1..5).map { transactionId("tx-$it") }
    }

    "opening balances are dropped before the cap, not counted against it" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val openings = (1..3).map {
            aTransaction(
                id = transactionId("ob-$it"),
                workspaceId = ws,
                accountId = account.id,
                type = TransactionType.OPENING_BALANCE,
            )
        }
        val expenses = (1..6).map {
            aTransaction(
                id = transactionId("tx-$it"),
                workspaceId = ws,
                accountId = account.id,
                type = TransactionType.EXPENSE,
            )
        }
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(openings + expenses),
        )

        // The openings sort first, so capping before filtering would spend three of the five slots
        // on them and leave only tx-1 and tx-2 on screen.
        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.transactions.map { it.id } shouldContainExactly (1..5).map { transactionId("tx-$it") }
    }

    "a transaction with no note still has something to render as a title" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(
                listOf(
                    aTransaction(
                        id = transactionId("tx-1"),
                        workspaceId = ws,
                        accountId = account.id,
                        note = "   ",
                        type = TransactionType.EXPENSE,
                    ),
                    aTransaction(
                        id = transactionId("tx-2"),
                        workspaceId = ws,
                        accountId = account.id,
                        note = "Coffee",
                        type = TransactionType.INCOME,
                    ),
                ),
            ),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.transactions.map { it.title } shouldContainExactly listOf("No description", "Coffee")
        content.transactions.map { it.isExpense } shouldContainExactly listOf(true, false)
    }

    "the goals widget shows two rows at most" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val goals = (1..4).map { aGoal(id = goalId("g-$it"), workspaceId = ws, title = "Goal $it") }
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(emptyList()),
            goals = FakeSavingsGoalRepository(goals),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.goals.map { it.name } shouldContainExactly listOf("Goal 1", "Goal 2")
    }

    "the offline build carries its flag onto the dashboard" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            hostCapabilities = FakeHostCapabilities.offline(),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().isOffline shouldBe true
    }
})
