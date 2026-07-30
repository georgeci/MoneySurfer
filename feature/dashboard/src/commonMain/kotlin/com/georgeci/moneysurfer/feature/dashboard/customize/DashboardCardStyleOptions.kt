package com.georgeci.moneysurfer.feature.dashboard.customize

import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSpan
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightsVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryVariant
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_size_compact
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_size_expanded
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_span_full
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_span_half
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_span_third
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_span_two_thirds
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_style_summary
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_balance_classic
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_balance_inline
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_balance_minimal
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_balance_stacked
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_insights_carousel
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_insights_list
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_spent_bar
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_spent_chips
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_spent_gauge
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_spent_multi
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_spent_ring
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_accounts
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_balance
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_budgets
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_burn_rate
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_goals
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_insights
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_quick_actions
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_recent
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_safe_to_spend
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_spent_by_category
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_spent_month
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One entry of a widget's variant list: the key persisted in
 * [com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle.variant] and the name the picker
 * shows for it.
 */
internal data class DashboardVariantOption(
    val key: String,
    val label: StringResource,
)

/**
 * The variants a widget offers, or an empty list when it only has a size. The keys come from the
 * widget's own enum in `uikit`, so the picker cannot offer a treatment the widget will not draw —
 * and a widget that gains variants later shows up here without the persistence format changing.
 */
internal fun DashboardWidgetType.variantOptions(): List<DashboardVariantOption> = when (this) {
    DashboardWidgetType.Balance -> BALANCE_VARIANTS
    DashboardWidgetType.SpentByCategory -> SPENT_BY_CATEGORY_VARIANTS
    DashboardWidgetType.Insights -> INSIGHTS_VARIANTS
    DashboardWidgetType.QuickActions,
    DashboardWidgetType.SpentMonth,
    DashboardWidgetType.SafeToSpend,
    DashboardWidgetType.BurnRate,
    DashboardWidgetType.Budgets,
    DashboardWidgetType.Accounts,
    DashboardWidgetType.Goals,
    DashboardWidgetType.RecentTransactions,
    -> emptyList()
}

/**
 * Built from the enum rather than hand-listed, so a treatment added to [SurferBalanceVariant] is a
 * compile error here until it is given a name — never a variant the widget draws but the picker
 * cannot offer.
 */
private val BALANCE_VARIANTS = SurferBalanceVariant.entries.map {
    DashboardVariantOption(key = it.name, label = it.labelResource())
}

/** Same construction as [BALANCE_VARIANTS], for the same reason. */
private val INSIGHTS_VARIANTS = SurferInsightsVariant.entries.map {
    DashboardVariantOption(key = it.name, label = it.labelResource())
}

private fun SurferInsightsVariant.labelResource(): StringResource = when (this) {
    SurferInsightsVariant.List -> Res.string.dashboard_customize_variant_insights_list
    SurferInsightsVariant.Carousel -> Res.string.dashboard_customize_variant_insights_carousel
}

private fun SurferBalanceVariant.labelResource(): StringResource = when (this) {
    SurferBalanceVariant.Classic -> Res.string.dashboard_customize_variant_balance_classic
    SurferBalanceVariant.Stacked -> Res.string.dashboard_customize_variant_balance_stacked
    SurferBalanceVariant.Inline -> Res.string.dashboard_customize_variant_balance_inline
    SurferBalanceVariant.Minimal -> Res.string.dashboard_customize_variant_balance_minimal
}

/** Same deal as [BALANCE_VARIANTS]: built from the enum the widget itself switches on. */
private val SPENT_BY_CATEGORY_VARIANTS = SurferSpentByCategoryVariant.entries.map {
    DashboardVariantOption(key = it.name, label = it.labelResource())
}

private fun SurferSpentByCategoryVariant.labelResource(): StringResource = when (this) {
    SurferSpentByCategoryVariant.Bar -> Res.string.dashboard_customize_variant_spent_bar
    SurferSpentByCategoryVariant.Ring -> Res.string.dashboard_customize_variant_spent_ring
    SurferSpentByCategoryVariant.Gauge -> Res.string.dashboard_customize_variant_spent_gauge
    SurferSpentByCategoryVariant.Chips -> Res.string.dashboard_customize_variant_spent_chips
    SurferSpentByCategoryVariant.Multi -> Res.string.dashboard_customize_variant_spent_multi
}

/**
 * The variant a widget is actually drawing, or null when it has none. A stored key the build does
 * not know — a layout written before the widget had variants, or by a newer build — resolves to the
 * first option, which is the fallback every widget's own `fromKey` applies. The picker and the row
 * summary both go through this, so they cannot disagree about what is selected.
 */
internal fun DashboardWidgetType.selectedVariant(cardStyle: DashboardCardStyle): DashboardVariantOption? {
    val options = variantOptions()
    return options.firstOrNull { it.key == cardStyle.variant } ?: options.firstOrNull()
}

internal fun DashboardWidgetSize.labelResource(): StringResource = when (this) {
    DashboardWidgetSize.Expanded -> Res.string.dashboard_customize_size_expanded
    DashboardWidgetSize.Compact -> Res.string.dashboard_customize_size_compact
}

internal fun DashboardWidgetSpan.labelResource(): StringResource = when (this) {
    DashboardWidgetSpan.Full -> Res.string.dashboard_customize_span_full
    DashboardWidgetSpan.TwoThirds -> Res.string.dashboard_customize_span_two_thirds
    DashboardWidgetSpan.Half -> Res.string.dashboard_customize_span_half
    DashboardWidgetSpan.Third -> Res.string.dashboard_customize_span_third
}

/**
 * What the customize row says under a widget's name — "Full", or "Full · Classic" once the widget
 * has variants to distinguish, or "Full · Classic · Half" once there is a grid too.
 *
 * The pill must name exactly what the sheet behind it can change, so it takes the same two gates the
 * sheet does: [showStyle] is the host's `dashboard_widget_style` build key, [showSpan] is whether the
 * window is wide enough for spans to mean anything. With both off the row draws no pill at all, so
 * this is never called for an empty summary.
 */
@Composable
internal fun cardStyleSummary(
    item: DashboardLayoutItem,
    showStyle: Boolean = true,
    showSpan: Boolean = false,
): String {
    val style = if (showStyle) styleSummary(item) else null
    val span = if (showSpan) stringResource(item.span.labelResource()) else null
    return when {
        style != null && span != null ->
            stringResource(Res.string.dashboard_customize_style_summary, style, span)
        else -> style ?: span.orEmpty()
    }
}

/** The size, plus the variant when the widget has any to tell apart. */
@Composable
private fun styleSummary(item: DashboardLayoutItem): String {
    val size = stringResource(item.cardStyle.size.labelResource())
    val variant = item.type.selectedVariant(item.cardStyle) ?: return size
    return stringResource(Res.string.dashboard_customize_style_summary, size, stringResource(variant.label))
}

/**
 * Widget labels for the customize list and the style sheet. Deliberately separate from the strings
 * the widgets render as their own headings: a widget needs a name here even when its card shows
 * none.
 */
internal fun DashboardWidgetType.titleResource(): StringResource = when (this) {
    DashboardWidgetType.Balance -> Res.string.dashboard_customize_widget_balance
    DashboardWidgetType.QuickActions -> Res.string.dashboard_customize_widget_quick_actions
    DashboardWidgetType.SpentMonth -> Res.string.dashboard_customize_widget_spent_month
    DashboardWidgetType.SafeToSpend -> Res.string.dashboard_customize_widget_safe_to_spend
    DashboardWidgetType.BurnRate -> Res.string.dashboard_customize_widget_burn_rate
    DashboardWidgetType.Budgets -> Res.string.dashboard_customize_widget_budgets
    DashboardWidgetType.SpentByCategory -> Res.string.dashboard_customize_widget_spent_by_category
    DashboardWidgetType.Accounts -> Res.string.dashboard_customize_widget_accounts
    DashboardWidgetType.Insights -> Res.string.dashboard_customize_widget_insights
    DashboardWidgetType.Goals -> Res.string.dashboard_customize_widget_goals
    DashboardWidgetType.RecentTransactions -> Res.string.dashboard_customize_widget_recent
}
