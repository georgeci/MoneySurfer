package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.FakeRecurringRuleRepository
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aRecurringRule
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.insight.InsightTone
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
 * Which sentence the insight engine's output maps to.
 *
 * Split out of `DashboardViewModelTest`: the dashboard composes every feature's numbers, so
 * one spec file for all of it grew past detekt's size limit and collided on every dashboard
 * PR. Shared fakes live in `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardInsightsViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "a rise maps to the warning sentence, with both amounts formatted in the base currency" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(emptyList()),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = spendOf(current = 400.dollars, previous = 300.dollars),
            recurringRules = FakeRecurringRuleRepository(
                listOf(aRecurringRule(workspaceId = ws, categoryId = DINING, amount = 12.dollars)),
            ),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        // Warn before Neutral: the compact card shows one, and it should be the actionable one.
        content.insights.map { it.kind } shouldContainExactly listOf(
            InsightKind.CategoryUp,
            InsightKind.PeriodUp,
            InsightKind.Subscriptions,
        )

        val category = content.insights.first()
        category.tone shouldBe InsightTone.Warn
        category.label shouldBe "Dining"
        category.percent shouldBe 33
        category.amount shouldBe MoneyFormatter.format(400.dollars, USD)
        category.comparison shouldBe MoneyFormatter.format(300.dollars, USD)

        val subscriptions = content.insights.last()
        subscriptions.tone shouldBe InsightTone.Neutral
        subscriptions.count shouldBe 1
        subscriptions.amount shouldBe MoneyFormatter.format(12.dollars, USD)
    }

    "a fall maps to the saving sentence rather than the same one with a sign" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(emptyList()),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = spendOf(current = 200.dollars, previous = 300.dollars),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.insights.map { it.kind } shouldContainExactly listOf(
            InsightKind.CategoryDown,
            InsightKind.PeriodDown,
        )
        content.insights.forEach { it.tone shouldBe InsightTone.Good }
    }

    "a period that barely moved is neutral, not a win" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(emptyList()),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = spendOf(current = 305.dollars, previous = 300.dollars),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.insights.map { it.kind } shouldContainExactly listOf(InsightKind.PeriodFlat)
        content.insights.single().tone shouldBe InsightTone.Neutral
    }
})
