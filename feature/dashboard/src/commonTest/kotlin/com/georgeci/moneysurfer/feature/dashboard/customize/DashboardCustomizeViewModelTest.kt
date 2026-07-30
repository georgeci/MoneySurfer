package com.georgeci.moneysurfer.feature.dashboard.customize

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSpan
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.utils.AsyncState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardCustomizeViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the screen opens on the stored layout, normalized" {
        val stored = DashboardLayoutConfig(items = listOf(DashboardLayoutItem(DashboardWidgetType.Goals)))
        val viewModel = DashboardCustomizeViewModel(FakeUiPreferences(dashboardLayout = stored), FakeHostCapabilities())

        viewModel.layout().items.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Goals,
            DashboardWidgetType.Balance,
            DashboardWidgetType.QuickActions,
            DashboardWidgetType.SpentMonth,
            DashboardWidgetType.SafeToSpend,
            DashboardWidgetType.BurnRate,
            DashboardWidgetType.Budgets,
            DashboardWidgetType.SpentByCategory,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.CategoriesDonut,
            DashboardWidgetType.Recurring,
            DashboardWidgetType.Insights,
            DashboardWidgetType.RecentTransactions,
        )
    }

    "switching a widget off drops it from the dashboard and persists the layout" {
        val preferences = FakeUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())

        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetEnabledChange(DashboardWidgetType.Goals, enabled = false),
        )

        viewModel.layout().enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Balance,
            DashboardWidgetType.QuickActions,
            DashboardWidgetType.SpentMonth,
            DashboardWidgetType.SafeToSpend,
            DashboardWidgetType.BurnRate,
            DashboardWidgetType.Budgets,
            DashboardWidgetType.SpentByCategory,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.CategoriesDonut,
            DashboardWidgetType.Recurring,
            DashboardWidgetType.Insights,
            DashboardWidgetType.RecentTransactions,
        )
        viewModel.layout().disabledItems.map { it.type } shouldContainExactly listOf(DashboardWidgetType.Goals)
        preferences.dashboardLayout.flow.first() shouldBe viewModel.layout()
    }

    "switching a widget back on returns it to the dashboard, last" {
        val preferences = FakeUiPreferences(
            dashboardLayout = DashboardLayoutConfig.DEFAULT
                .withWidgetEnabled(DashboardWidgetType.Balance, enabled = false),
        )
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())

        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetEnabledChange(DashboardWidgetType.Balance, enabled = true),
        )

        viewModel.layout().enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.QuickActions,
            DashboardWidgetType.SpentMonth,
            DashboardWidgetType.SafeToSpend,
            DashboardWidgetType.BurnRate,
            DashboardWidgetType.Budgets,
            DashboardWidgetType.SpentByCategory,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.CategoriesDonut,
            DashboardWidgetType.Recurring,
            DashboardWidgetType.Insights,
            DashboardWidgetType.Goals,
            DashboardWidgetType.RecentTransactions,
            DashboardWidgetType.Balance,
        )
        preferences.dashboardLayout.flow.first() shouldBe viewModel.layout()
    }

    "dragging a widget onto another one persists the new order" {
        val preferences = FakeUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())

        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetMove(
                from = DashboardWidgetType.RecentTransactions,
                to = DashboardWidgetType.Balance,
            ),
        )

        viewModel.layout().enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.RecentTransactions,
            DashboardWidgetType.Balance,
            DashboardWidgetType.QuickActions,
            DashboardWidgetType.SpentMonth,
            DashboardWidgetType.SafeToSpend,
            DashboardWidgetType.BurnRate,
            DashboardWidgetType.Budgets,
            DashboardWidgetType.SpentByCategory,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.CategoriesDonut,
            DashboardWidgetType.Recurring,
            DashboardWidgetType.Insights,
            DashboardWidgetType.Goals,
        )
        preferences.dashboardLayout.flow.first() shouldBe viewModel.layout()
    }

    "a move that changes nothing is not written back" {
        val preferences = RecordingUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())

        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetMove(
                from = DashboardWidgetType.Balance,
                to = DashboardWidgetType.Balance,
            ),
        )

        viewModel.layout() shouldBe DashboardLayoutConfig.DEFAULT
        preferences.layoutWrites shouldBe 0
    }

    "a burst of moves persists the layout the last one produced" {
        val preferences = FakeUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())

        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetMove(DashboardWidgetType.Goals, DashboardWidgetType.Balance),
        )
        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetMove(DashboardWidgetType.RecentTransactions, DashboardWidgetType.Goals),
        )
        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetMove(DashboardWidgetType.Accounts, DashboardWidgetType.Goals),
        )

        preferences.dashboardLayout.flow.first() shouldBe viewModel.layout()
    }

    "picking a card style persists it without moving the widget" {
        val preferences = FakeUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())

        viewModel.onEvent(
            DashboardCustomizeEvent.OnCardStyleChange(
                type = DashboardWidgetType.Balance,
                cardStyle = DashboardCardStyle(DashboardWidgetSize.Compact, variant = "Inline"),
            ),
        )

        viewModel.layout().enabledItems.map { it.type } shouldContainExactly
            DashboardLayoutConfig.DEFAULT.items.map { it.type }
        viewModel.layout().items.single { it.type == DashboardWidgetType.Balance }.cardStyle shouldBe
            DashboardCardStyle(DashboardWidgetSize.Compact, variant = "Inline")
        preferences.dashboardLayout.flow.first() shouldBe viewModel.layout()
    }

    "picking the style a widget already has is not written back" {
        val preferences = RecordingUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())

        viewModel.onEvent(
            DashboardCustomizeEvent.OnCardStyleChange(DashboardWidgetType.Balance, DashboardCardStyle.EXPANDED),
        )

        preferences.layoutWrites shouldBe 0
    }

    "picking a grid width persists it without touching the widget's card style or slot" {
        val preferences = FakeUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())
        val styleBefore = viewModel.layout().items.single { it.type == DashboardWidgetType.Balance }.cardStyle

        viewModel.onEvent(
            DashboardCustomizeEvent.OnSpanChange(DashboardWidgetType.Balance, DashboardWidgetSpan.Third),
        )

        viewModel.layout().items.map { it.type } shouldContainExactly
            DashboardLayoutConfig.DEFAULT.items.map { it.type }
        viewModel.layout().items.single { it.type == DashboardWidgetType.Balance }.let {
            it.span shouldBe DashboardWidgetSpan.Third
            it.cardStyle shouldBe styleBefore
        }
        preferences.dashboardLayout.flow.first() shouldBe viewModel.layout()
    }

    "picking the grid width a widget already has is not written back" {
        val preferences = RecordingUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())
        val current = viewModel.layout().items.single { it.type == DashboardWidgetType.Balance }.span

        viewModel.onEvent(DashboardCustomizeEvent.OnSpanChange(DashboardWidgetType.Balance, current))

        preferences.layoutWrites shouldBe 0
    }

    "dragging a widget onto a row in the other section moves it there" {
        val preferences = FakeUiPreferences(
            dashboardLayout = DashboardLayoutConfig.DEFAULT
                .withWidgetEnabled(DashboardWidgetType.Goals, enabled = false),
        )
        val viewModel = DashboardCustomizeViewModel(preferences, FakeHostCapabilities())

        // Goals is the only switched-off widget; dropping it on Balance puts it back on the
        // dashboard, first, rather than appending it the way the +/- button does.
        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetMove(
                from = DashboardWidgetType.Goals,
                to = DashboardWidgetType.Balance,
            ),
        )

        viewModel.layout().enabledItems.first().type shouldBe DashboardWidgetType.Goals
        viewModel.layout().disabledItems.shouldBeEmpty()
        preferences.dashboardLayout.flow.first() shouldBe viewModel.layout()
    }

    "the card-style picker follows the host's build flag" {
        DashboardCustomizeViewModel(FakeUiPreferences(), FakeHostCapabilities())
            .widgetStyleEnabled shouldBe false
        DashboardCustomizeViewModel(FakeUiPreferences(), FakeHostCapabilities(dashboardWidgetStyle = true))
            .widgetStyleEnabled shouldBe true
    }

    "the back action leaves the screen" {
        val viewModel = DashboardCustomizeViewModel(FakeUiPreferences(), FakeHostCapabilities())

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(DashboardCustomizeEvent.OnBackClick)
            awaitItem() shouldBe DashboardCustomizeEffect.NavigateBack
        }
    }
})

private fun DashboardCustomizeViewModel.layout(): DashboardLayoutConfig =
    value.shouldBeInstanceOf<AsyncState.Content<DashboardLayoutConfig>>().value

/** Counts layout writes, so "the edit was a no-op" can be asserted as "nothing was persisted". */
private class RecordingUiPreferences : UiPreferences by FakeUiPreferences() {
    var layoutWrites: Int = 0
        private set

    private val delegate: Pref<DashboardLayoutConfig> = Pref.inMemory(DashboardLayoutConfig.DEFAULT)

    override val dashboardLayout: Pref<DashboardLayoutConfig> = object : Pref<DashboardLayoutConfig> {
        override val flow = delegate.flow
        override suspend fun set(value: DashboardLayoutConfig) {
            layoutWrites++
            delegate.set(value)
        }
    }
}
