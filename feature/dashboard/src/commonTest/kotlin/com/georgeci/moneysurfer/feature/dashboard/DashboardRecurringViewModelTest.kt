package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.FakeRecurringRuleRepository
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.recurringRuleId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.RecurringFrequency
import com.georgeci.moneysurfer.domain.model.RecurringRule
import com.georgeci.moneysurfer.domain.model.RecurringSchedule
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/**
 * What the upcoming-payments card is handed: the next occurrence of each rule, soonest first.
 *
 * The shared clock is fixed at 2024-01-01, so every expected date below is read against that day.
 * Split from `DashboardViewModelTest` for the reason the insights spec is — shared fakes live in
 * `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRecurringViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "rules arrive dated and sorted, with the ones due today or tomorrow tinted" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(emptyList()),
            transactions = FakeTransactionRepository(emptyList()),
            recurringRules = FakeRecurringRuleRepository(
                listOf(
                    // The 5th of every month, so the next one is four days out.
                    ruleIn(ws, "rent", RecurringFrequency.MONTHLY, daysOfMonth = setOf(5), amount = 980.dollars),
                    // Every day, so it is due today.
                    ruleIn(ws, "coffee", RecurringFrequency.DAILY, amount = 3.dollars),
                ),
            ),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.upcoming.map { it.name } shouldContainExactly listOf("coffee", "rent")
        content.upcoming.map { it.dueDateIso } shouldContainExactly listOf("2024-01-01", "2024-01-05")
        content.upcoming.map { it.daysUntil } shouldContainExactly listOf(0, 4)
        content.upcoming.map { it.isImminent } shouldContainExactly listOf(true, false)
        content.upcoming.map { it.amountFormatted } shouldContainExactly listOf(
            MoneyFormatter.format(3.dollars, USD),
            MoneyFormatter.format(980.dollars, USD),
        )
    }

    "a paused rule is not listed" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(emptyList()),
            transactions = FakeTransactionRepository(emptyList()),
            recurringRules = FakeRecurringRuleRepository(
                listOf(ruleIn(ws, "gym", RecurringFrequency.DAILY, isActive = false)),
            ),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().upcoming.shouldBeEmpty()
    }
})

@Suppress("LongParameterList")
private fun ruleIn(
    ws: WorkspaceId,
    title: String,
    frequency: RecurringFrequency,
    daysOfMonth: Set<Int> = emptySet(),
    amount: Money = 10.dollars,
    isActive: Boolean = true,
) = RecurringRule(
    id = recurringRuleId(title),
    workspaceId = ws,
    title = title,
    amount = amount,
    categoryId = categoryId(),
    schedule = RecurringSchedule(frequency = frequency, daysOfMonth = daysOfMonth),
    startDate = LocalDate(2023, 1, 1),
    nextRunAt = null,
    isActive = isActive,
)
