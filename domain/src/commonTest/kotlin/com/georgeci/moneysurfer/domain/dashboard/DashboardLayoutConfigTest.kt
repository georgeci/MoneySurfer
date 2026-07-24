package com.georgeci.moneysurfer.domain.dashboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class DashboardLayoutConfigTest : StringSpec({

    "the default layout covers every widget type, enabled, in Variant A order" {
        DashboardLayoutConfig.DEFAULT.items.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Balance,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Goals,
            DashboardWidgetType.RecentTransactions,
        )
        DashboardLayoutConfig.DEFAULT.items.all { it.enabled } shouldBe true
    }

    "enabledItems keeps order and drops the switched-off widgets" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Goals),
                DashboardLayoutItem(DashboardWidgetType.Balance, enabled = false),
                DashboardLayoutItem(DashboardWidgetType.Accounts),
            ),
        )

        config.enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Goals,
            DashboardWidgetType.Accounts,
        )
    }

    "normalizing appends widgets a stored layout has never heard of" {
        val stored = DashboardLayoutConfig(items = listOf(DashboardLayoutItem(DashboardWidgetType.Goals)))

        stored.normalized().items.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Goals,
            DashboardWidgetType.Balance,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.RecentTransactions,
        )
    }

    "normalizing keeps the stored style of a widget it re-appends nothing for" {
        val stored = DashboardLayoutConfig(
            items = DashboardLayoutConfig.DEFAULT.items.map { it.copy(cardStyle = DashboardCardStyle.COMPACT) },
        )

        stored.normalized() shouldBe stored
    }

    "normalizing drops duplicate entries for the same widget, keeping the first" {
        val stored = DashboardLayoutConfig(
            items = DashboardLayoutConfig.DEFAULT.items +
                DashboardLayoutItem(DashboardWidgetType.Balance, enabled = false),
        )

        val normalized = stored.normalized()

        normalized.items shouldContainExactly DashboardLayoutConfig.DEFAULT.items
    }
})
