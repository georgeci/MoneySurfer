package com.georgeci.moneysurfer.domain.dashboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class DashboardLayoutConfigTest : StringSpec({

    "the default layout covers every widget type, enabled, in Variant A order" {
        DashboardLayoutConfig.DEFAULT.items.map { it.type } shouldContainExactly listOf(
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

    "moving a widget onto itself changes nothing" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(DashboardWidgetType.Accounts, enabled = false),
            ),
        )

        config.withWidgetMoved(DashboardWidgetType.Balance, DashboardWidgetType.Balance) shouldBe config
    }

    "moving a widget onto a switched-off one switches it off, in that slot" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(DashboardWidgetType.Goals),
                DashboardLayoutItem(DashboardWidgetType.Accounts, enabled = false),
            ),
        )

        val moved = config.withWidgetMoved(DashboardWidgetType.Balance, DashboardWidgetType.Accounts)

        moved.enabledItems.map { it.type } shouldContainExactly listOf(DashboardWidgetType.Goals)
        moved.disabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Balance,
        )
    }

    "moving a switched-off widget onto an enabled one switches it on, in that slot" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(DashboardWidgetType.Goals),
                DashboardLayoutItem(DashboardWidgetType.Accounts, enabled = false),
            ),
        )

        val moved = config.withWidgetMoved(DashboardWidgetType.Accounts, DashboardWidgetType.Goals)

        // Second, where it was dropped — not appended after the last enabled widget the way
        // `withWidgetEnabled` does it.
        moved.enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Balance,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Goals,
        )
        moved.disabledItems.shouldBeEmpty()
    }

    "a widget dragged across sections keeps its card style" {
        val styled = DashboardCardStyle(DashboardWidgetSize.Compact, variant = "strip")
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(DashboardWidgetType.Accounts, enabled = false, cardStyle = styled),
            ),
        )

        val moved = config.withWidgetMoved(DashboardWidgetType.Accounts, DashboardWidgetType.Balance)

        moved.items.single { it.type == DashboardWidgetType.Accounts }.cardStyle shouldBe styled
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

        config.withCardStyle(DashboardWidgetType.Balance, DashboardCardStyle.EXPANDED) shouldBe config
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

    "every row of the default grid is filled exactly, leaving no gap to pack around" {
        val rows = mutableListOf(0)
        DashboardLayoutConfig.DEFAULT.enabledItems.forEach { item ->
            if (rows.last() + item.span.columns > DashboardWidgetSpan.COLUMNS) rows += 0
            rows[rows.lastIndex] = rows.last() + item.span.columns
        }

        rows.all { it == DashboardWidgetSpan.COLUMNS } shouldBe true
    }

    "the widest span is the whole row, and it is what a widget gets by default" {
        DashboardWidgetSpan.Full.columns shouldBe DashboardWidgetSpan.COLUMNS
        DashboardLayoutItem(DashboardWidgetType.Balance).span shouldBe DashboardWidgetSpan.Full
    }

    "respanning a widget leaves its slot, its style and its neighbours alone" {
        val config = DashboardLayoutConfig.DEFAULT
            .withWidgetEnabled(DashboardWidgetType.Goals, enabled = false)
            .withCardStyle(DashboardWidgetType.Accounts, DashboardCardStyle.COMPACT)

        val respanned = config.withSpan(DashboardWidgetType.Accounts, DashboardWidgetSpan.Half)

        respanned.items.map { it.type } shouldContainExactly config.items.map { it.type }
        respanned.items.single { it.type == DashboardWidgetType.Accounts }.let {
            it.span shouldBe DashboardWidgetSpan.Half
            it.cardStyle shouldBe DashboardCardStyle.COMPACT
        }
        respanned.items.filterNot { it.type == DashboardWidgetType.Accounts } shouldContainExactly
            config.items.filterNot { it.type == DashboardWidgetType.Accounts }
    }

    "a switched-off widget keeps its span for when it comes back" {
        val config = DashboardLayoutConfig.DEFAULT
            .withWidgetEnabled(DashboardWidgetType.Goals, enabled = false)
            .withSpan(DashboardWidgetType.Goals, DashboardWidgetSpan.Third)

        config.withWidgetEnabled(DashboardWidgetType.Goals, enabled = true)
            .items.single { it.type == DashboardWidgetType.Goals }
            .span shouldBe DashboardWidgetSpan.Third
    }

    "a widget dragged across sections keeps its span" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(
                    DashboardWidgetType.Accounts,
                    enabled = false,
                    span = DashboardWidgetSpan.TwoThirds,
                ),
            ),
        )

        val moved = config.withWidgetMoved(DashboardWidgetType.Accounts, DashboardWidgetType.Balance)

        moved.items.single { it.type == DashboardWidgetType.Accounts }.span shouldBe
            DashboardWidgetSpan.TwoThirds
    }

    "respanning to the span a widget already has, or a widget the layout lacks, changes nothing" {
        val config = DashboardLayoutConfig(items = listOf(DashboardLayoutItem(DashboardWidgetType.Balance)))

        config.withSpan(DashboardWidgetType.Balance, DashboardWidgetSpan.Full) shouldBe config
        config.withSpan(DashboardWidgetType.Goals, DashboardWidgetSpan.Half) shouldBe config
    }

    "the default layout has something for the period switch to drive" {
        DashboardLayoutConfig.DEFAULT.hasPeriodScopedWidget shouldBe true
    }

    "switching off every period-scoped widget leaves the switch nothing to drive" {
        val periodScoped = DashboardWidgetType.entries.filter { it.isPeriodScoped }
        val config = periodScoped.fold(DashboardLayoutConfig.DEFAULT) { acc, type ->
            acc.withWidgetEnabled(type, enabled = false)
        }

        // The widgets are still in the layout — only switched off. A disabled one renders nothing,
        // so a switch above it would change nothing visible either.
        config.items.map { it.type } shouldContainAll periodScoped
        config.hasPeriodScopedWidget shouldBe false
    }
})
