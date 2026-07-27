package com.georgeci.moneysurfer.feature.dashboard.customize

import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceVariant
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_size_compact
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_size_expanded
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_style_summary
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_balance_classic
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_balance_inline
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_balance_minimal
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_variant_balance_stacked
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_accounts
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_balance
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_goals
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_widget_recent
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

private fun SurferBalanceVariant.labelResource(): StringResource = when (this) {
    SurferBalanceVariant.Classic -> Res.string.dashboard_customize_variant_balance_classic
    SurferBalanceVariant.Stacked -> Res.string.dashboard_customize_variant_balance_stacked
    SurferBalanceVariant.Inline -> Res.string.dashboard_customize_variant_balance_inline
    SurferBalanceVariant.Minimal -> Res.string.dashboard_customize_variant_balance_minimal
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

/**
 * What the customize row says under a widget's name — "Full", or "Full · Classic" once the widget
 * has variants to distinguish.
 */
@Composable
internal fun cardStyleSummary(type: DashboardWidgetType, cardStyle: DashboardCardStyle): String {
    val size = stringResource(cardStyle.size.labelResource())
    val variant = type.selectedVariant(cardStyle) ?: return size
    return stringResource(Res.string.dashboard_customize_style_summary, size, stringResource(variant.label))
}

/**
 * Widget labels for the customize list and the style sheet. Deliberately separate from the strings
 * the widgets render as their own headings: a widget needs a name here even when its card shows
 * none.
 */
internal fun DashboardWidgetType.titleResource(): StringResource = when (this) {
    DashboardWidgetType.Balance -> Res.string.dashboard_customize_widget_balance
    DashboardWidgetType.Accounts -> Res.string.dashboard_customize_widget_accounts
    DashboardWidgetType.Goals -> Res.string.dashboard_customize_widget_goals
    DashboardWidgetType.RecentTransactions -> Res.string.dashboard_customize_widget_recent
}
