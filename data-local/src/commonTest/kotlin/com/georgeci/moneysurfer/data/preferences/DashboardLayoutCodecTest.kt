package com.georgeci.moneysurfer.data.preferences

import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSpan
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class DashboardLayoutCodecTest : StringSpec({

    "a customised layout survives a round trip" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(
                    DashboardWidgetType.Goals,
                    cardStyle = DashboardCardStyle.COMPACT,
                    span = DashboardWidgetSpan.Half,
                ),
                DashboardLayoutItem(DashboardWidgetType.Balance, enabled = false),
                DashboardLayoutItem(
                    DashboardWidgetType.Accounts,
                    cardStyle = DashboardCardStyle(DashboardWidgetSize.Compact, variant = "strip"),
                    span = DashboardWidgetSpan.Third,
                ),
                // Every type, or the decode-side `normalized()` would append the missing one and
                // the round trip would fail for a reason that has nothing to do with the codec.
                DashboardLayoutItem(DashboardWidgetType.QuickActions),
                DashboardLayoutItem(DashboardWidgetType.SpentMonth),
                DashboardLayoutItem(DashboardWidgetType.SafeToSpend),
                DashboardLayoutItem(DashboardWidgetType.BurnRate),
                DashboardLayoutItem(DashboardWidgetType.Budgets),
                DashboardLayoutItem(DashboardWidgetType.SpentByCategory),
                DashboardLayoutItem(DashboardWidgetType.CategoriesDonut),
                DashboardLayoutItem(DashboardWidgetType.Insights),
                DashboardLayoutItem(DashboardWidgetType.RecentTransactions),
            ),
        )

        DashboardLayoutCodec.decode(DashboardLayoutCodec.encode(config)) shouldBe config
    }

    "a variant carrying the separators round-trips instead of splitting the layout" {
        val config = DashboardLayoutConfig(
            items = DashboardLayoutConfig.DEFAULT.items.map {
                it.copy(cardStyle = DashboardCardStyle(DashboardWidgetSize.Expanded, variant = "a|b:c%d"))
            },
        )

        val encoded = DashboardLayoutCodec.encode(config)

        DashboardLayoutCodec.decode(encoded) shouldBe config
        encoded.split('|').size shouldBe config.items.size
    }

    "a trailing empty variant field reads as no variant at all" {
        val decoded = DashboardLayoutCodec.decode("Goals:1:Expanded:")

        decoded.items.first().cardStyle.variant shouldBe null
    }

    "an empty store means the default layout" {
        DashboardLayoutCodec.decode("") shouldBe DashboardLayoutConfig.DEFAULT
    }

    "a widget this build does not know is skipped, and the known ones are kept" {
        val decoded = DashboardLayoutCodec.decode("Goals:1:Expanded|Cryptocurrency:1:Expanded")

        decoded.enabledItems.first().type shouldBe DashboardWidgetType.Goals
        decoded.items.map { it.type } shouldContainExactly listOf(
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
            DashboardWidgetType.Insights,
            DashboardWidgetType.RecentTransactions,
        )
    }

    "garbage decodes to the default layout instead of an empty dashboard" {
        DashboardLayoutCodec.decode("¯\\_(ツ)_/¯") shouldBe DashboardLayoutConfig.DEFAULT
    }

    "an unreadable size falls back to Hero rather than dropping the widget" {
        val decoded = DashboardLayoutCodec.decode("Goals:1:Enormous")

        decoded.items.first() shouldBe DashboardLayoutItem(
            DashboardWidgetType.Goals,
            cardStyle = DashboardCardStyle.EXPANDED,
        )
    }

    "a layout written before spans existed reads as full width, not as a dropped widget" {
        val decoded = DashboardLayoutCodec.decode("Goals:1:Compact|Balance:1:Expanded:Inline")

        decoded.items.first { it.type == DashboardWidgetType.Goals }.span shouldBe DashboardWidgetSpan.Full
        decoded.items.first { it.type == DashboardWidgetType.Balance }.let {
            it.span shouldBe DashboardWidgetSpan.Full
            it.cardStyle shouldBe DashboardCardStyle(DashboardWidgetSize.Expanded, variant = "Inline")
        }
    }

    "an unreadable span falls back to full width, keeping the rest of the item" {
        val decoded = DashboardLayoutCodec.decode("Goals:1:Compact:strip:Sideways")

        decoded.items.first() shouldBe DashboardLayoutItem(
            DashboardWidgetType.Goals,
            cardStyle = DashboardCardStyle(DashboardWidgetSize.Compact, variant = "strip"),
            span = DashboardWidgetSpan.Full,
        )
    }

    "a widget at the default span writes no span field, and a narrower one writes both trailing fields" {
        val config = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Goals),
                DashboardLayoutItem(DashboardWidgetType.Balance, span = DashboardWidgetSpan.Third),
            ),
        )

        DashboardLayoutCodec.encode(config) shouldBe "Goals:1:Expanded|Balance:1:Expanded::Third"
    }
})
