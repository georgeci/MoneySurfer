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

    "disabledItems is the complement of enabledItems" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Goals),
                DashboardLayoutItem(DashboardWidgetType.Balance, enabled = false),
                DashboardLayoutItem(DashboardWidgetType.Accounts),
            ),
        )

        config.disabledItems.map { it.type } shouldContainExactly listOf(DashboardWidgetType.Balance)
    }

    "switching a widget off keeps its card style and parks it after the enabled ones" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance, cardStyle = DashboardCardStyle.COMPACT),
                DashboardLayoutItem(DashboardWidgetType.Accounts),
                DashboardLayoutItem(DashboardWidgetType.Goals),
            ),
        )

        val updated = config.withWidgetEnabled(DashboardWidgetType.Balance, enabled = false)

        updated.items.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Goals,
            DashboardWidgetType.Balance,
        )
        updated.enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Goals,
        )
        updated.items.last().cardStyle shouldBe DashboardCardStyle.COMPACT
    }

    "switching a widget on appends it after the last enabled one, before the still-off ones" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(DashboardWidgetType.Accounts, enabled = false),
                DashboardLayoutItem(DashboardWidgetType.Goals, enabled = false),
            ),
        )

        val updated = config.withWidgetEnabled(DashboardWidgetType.Goals, enabled = true)

        updated.items.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Balance,
            DashboardWidgetType.Goals,
            DashboardWidgetType.Accounts,
        )
        updated.enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Balance,
            DashboardWidgetType.Goals,
        )
    }

    "switching a widget to the state it already has changes nothing" {
        val config = DashboardLayoutConfig.DEFAULT

        config.withWidgetEnabled(DashboardWidgetType.Balance, enabled = true) shouldBe config
    }

    "moving a widget down puts it in the slot of the widget it landed on" {
        val moved = DashboardLayoutConfig.DEFAULT.withWidgetMoved(
            from = DashboardWidgetType.Balance,
            to = DashboardWidgetType.Goals,
        )

        moved.items.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Goals,
            DashboardWidgetType.Balance,
            DashboardWidgetType.RecentTransactions,
        )
    }

    "moving a widget up puts it in the slot of the widget it landed on" {
        val moved = DashboardLayoutConfig.DEFAULT.withWidgetMoved(
            from = DashboardWidgetType.RecentTransactions,
            to = DashboardWidgetType.Balance,
        )

        moved.items.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.RecentTransactions,
            DashboardWidgetType.Balance,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Goals,
        )
    }

    "moving reorders only the enabled widgets and leaves the switched-off ones behind them" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(DashboardWidgetType.Accounts, enabled = false),
                DashboardLayoutItem(DashboardWidgetType.Goals),
            ),
        )

        val moved = config.withWidgetMoved(from = DashboardWidgetType.Goals, to = DashboardWidgetType.Balance)

        moved.items.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Goals,
            DashboardWidgetType.Balance,
            DashboardWidgetType.Accounts,
        )
    }

    "moving a widget onto itself, or onto a switched-off one, changes nothing" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(DashboardWidgetType.Accounts, enabled = false),
            ),
        )

        config.withWidgetMoved(DashboardWidgetType.Balance, DashboardWidgetType.Balance) shouldBe config
        config.withWidgetMoved(DashboardWidgetType.Balance, DashboardWidgetType.Accounts) shouldBe config
    }

    "restyling a widget leaves its slot and its neighbours alone" {
        val config = DashboardLayoutConfig.DEFAULT
            .withWidgetEnabled(DashboardWidgetType.Goals, enabled = false)

        val restyled = config.withCardStyle(
            DashboardWidgetType.Accounts,
            DashboardCardStyle(DashboardWidgetSize.Compact, variant = "strip"),
        )

        restyled.items.map { it.type } shouldContainExactly config.items.map { it.type }
        restyled.items.single { it.type == DashboardWidgetType.Accounts }.cardStyle shouldBe
            DashboardCardStyle(DashboardWidgetSize.Compact, variant = "strip")
        restyled.items.filterNot { it.type == DashboardWidgetType.Accounts } shouldContainExactly
            config.items.filterNot { it.type == DashboardWidgetType.Accounts }
    }

    "a switched-off widget can be restyled too — it keeps the style when it comes back" {
        val config = DashboardLayoutConfig.DEFAULT
            .withWidgetEnabled(DashboardWidgetType.Goals, enabled = false)
            .withCardStyle(DashboardWidgetType.Goals, DashboardCardStyle.COMPACT)

        config.withWidgetEnabled(DashboardWidgetType.Goals, enabled = true)
            .items.single { it.type == DashboardWidgetType.Goals }
            .cardStyle shouldBe DashboardCardStyle.COMPACT
    }

    "restyling to the style a widget already has, or a widget the layout lacks, changes nothing" {
        val config = DashboardLayoutConfig(items = listOf(DashboardLayoutItem(DashboardWidgetType.Balance)))

        config.withCardStyle(DashboardWidgetType.Balance, DashboardCardStyle.HERO) shouldBe config
        config.withCardStyle(DashboardWidgetType.Goals, DashboardCardStyle.COMPACT) shouldBe config
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
