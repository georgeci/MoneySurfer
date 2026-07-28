package com.georgeci.moneysurfer.feature.dashboard.customize

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.utils.AsyncState
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

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardCustomizeViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the screen opens on the stored layout, normalized" {
        val stored = DashboardLayoutConfig(items = listOf(DashboardLayoutItem(DashboardWidgetType.Goals)))
        val viewModel = DashboardCustomizeViewModel(FakeUiPreferences(dashboardLayout = stored))

        viewModel.layout().items.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Goals,
            DashboardWidgetType.Balance,
            DashboardWidgetType.QuickActions,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Insights,
            DashboardWidgetType.RecentTransactions,
        )
    }

    "switching a widget off drops it from the dashboard and persists the layout" {
        val preferences = FakeUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences)

        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetEnabledChange(DashboardWidgetType.Goals, enabled = false),
        )

        viewModel.layout().enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Balance,
            DashboardWidgetType.QuickActions,
            DashboardWidgetType.Accounts,
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
        val viewModel = DashboardCustomizeViewModel(preferences)

        viewModel.onEvent(
            DashboardCustomizeEvent.OnWidgetEnabledChange(DashboardWidgetType.Balance, enabled = true),
        )

        viewModel.layout().enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.QuickActions,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Insights,
            DashboardWidgetType.Goals,
            DashboardWidgetType.RecentTransactions,
            DashboardWidgetType.Balance,
        )
        preferences.dashboardLayout.flow.first() shouldBe viewModel.layout()
    }

    "dragging a widget onto another one persists the new order" {
        val preferences = FakeUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences)

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
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Insights,
            DashboardWidgetType.Goals,
        )
        preferences.dashboardLayout.flow.first() shouldBe viewModel.layout()
    }

    "a move that changes nothing is not written back" {
        val preferences = RecordingUiPreferences()
        val viewModel = DashboardCustomizeViewModel(preferences)

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
        val viewModel = DashboardCustomizeViewModel(preferences)

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
        val viewModel = DashboardCustomizeViewModel(preferences)

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
        val viewModel = DashboardCustomizeViewModel(preferences)

        viewModel.onEvent(
            DashboardCustomizeEvent.OnCardStyleChange(DashboardWidgetType.Balance, DashboardCardStyle.EXPANDED),
        )

        preferences.layoutWrites shouldBe 0
    }

    "the back action leaves the screen" {
        val viewModel = DashboardCustomizeViewModel(FakeUiPreferences())

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
