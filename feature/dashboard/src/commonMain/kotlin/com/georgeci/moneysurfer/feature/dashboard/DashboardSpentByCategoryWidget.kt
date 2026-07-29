package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.widgets.SurferCategorySpendCap
import com.georgeci.moneysurfer.uikit.widgets.SurferCategorySpendItem
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryEmpty
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryWidget
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_empty_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_empty_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_of_cap
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_over_cap
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_share
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_status_over
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_status_warn
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_uncategorized
import org.jetbrains.compose.resources.stringResource

/**
 * Where this month's money went, category by category, in whichever of the five treatments the
 * card style names.
 *
 * Like safe-to-spend this one keeps drawing with nothing behind it: a month with no expenses yet is
 * a state the widget is *for*, not a reason to leave a gap in the column.
 */
@Composable
internal fun SpentByCategoryWidget(state: DashboardState.Content, variant: String?) {
    SurferSpentByCategoryWidget(
        title = stringResource(Res.string.dashboard_spent_by_category_title),
        items = state.spentByCategory.map { it.toWidgetItem() },
        variant = SurferSpentByCategoryVariant.fromKey(variant),
        empty = SurferSpentByCategoryEmpty(
            title = stringResource(Res.string.dashboard_spent_by_category_empty_title),
            subtitle = stringResource(Res.string.dashboard_spent_by_category_empty_subtitle),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .testTag(DashboardTestTags.SpentByCategory),
    )
}

/**
 * The row as the card draws it. The tint comes from the category's stored hue through the one
 * resolver every category bubble goes through, so a category keeps the same colour here as on the
 * screens that own it.
 *
 * The uncategorized bucket has no stored appearance, so its tint is hashed from the fixed
 * [UNCATEGORIZED_ID] rather than from the label beside it — the label is translated, and a bucket
 * that changed colour when the user switched language would look like a different category.
 */
@Composable
private fun CategorySpendUi.toWidgetItem(): SurferCategorySpendItem {
    val fallbackName = stringResource(Res.string.dashboard_spent_by_category_uncategorized)
    val id = categoryId ?: UNCATEGORIZED_ID
    return SurferCategorySpendItem(
        id = id,
        name = name ?: fallbackName,
        amount = spentFormatted,
        share = share,
        caption = caption(),
        // The same resolver every category bubble goes through: the stored hue snapped to the
        // palette, falling back to hashing the id for a row that stored none.
        tint = SurferCategoryPalette.tintForHue(hue ?: NO_STORED_HUE) ?: SurferCategoryPalette.tintFor(id),
        cap = cap?.toWidgetCap(),
    )
}

/**
 * The line that names what the row's meter measures. A capped category is measured against its cap,
 * so the caption states the cap; an uncapped one is measured against the period's spend, so it
 * states the share. The two must never be told apart by the shape alone.
 */
@Composable
private fun CategorySpendUi.caption(): String {
    val limit = cap ?: return stringResource(Res.string.dashboard_spent_by_category_share, sharePercent)
    return if (limit.status == BudgetStatus.OVER) {
        stringResource(Res.string.dashboard_spent_by_category_over_cap, limit.limitFormatted)
    } else {
        stringResource(Res.string.dashboard_spent_by_category_of_cap, spentFormatted, limit.limitFormatted)
    }
}

@Composable
private fun CategoryCapUi.toWidgetCap(): SurferCategorySpendCap = SurferCategorySpendCap(
    progress = progress,
    status = status.toWidgetStatus(),
    statusLabel = status.statusLabel(),
)

/** Only the two states worth interrupting a row for carry a word; OK is the quiet default. */
@Composable
private fun BudgetStatus.statusLabel(): String? = when (this) {
    BudgetStatus.OK -> null
    BudgetStatus.WARN -> stringResource(Res.string.dashboard_spent_by_category_status_warn)
    BudgetStatus.OVER -> stringResource(Res.string.dashboard_spent_by_category_status_over)
}

/** List key for the uncategorized bucket, which has no category id to be keyed by. */
private const val UNCATEGORIZED_ID = "uncategorized"

/**
 * The "never stored" hue sentinel — `SurferCategoryPalette.tintForHue` reads anything off the
 * colour wheel as absent and falls back to hashing the id.
 */
private const val NO_STORED_HUE = -1
