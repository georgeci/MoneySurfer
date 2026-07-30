package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.widgets.SurferCategoriesDonutWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferDonutSegment
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_categories_donut_center_label
import moneysurfer.feature.dashboard.generated.resources.dashboard_categories_donut_empty_center
import moneysurfer.feature.dashboard.generated.resources.dashboard_categories_donut_empty_legend
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_uncategorized
import org.jetbrains.compose.resources.stringResource

/**
 * The selected period's spend as a donut: one arc per category, largest first, with the legend
 * beside it and the period's total in the middle.
 *
 * Reads the same [DashboardState.Content.spentByCategory] rows the spent-by-category card does, so
 * the two can never disagree about what the period holds — including the uncategorized bucket,
 * which arrives as a real slice and is drawn as one rather than being folded away.
 *
 * How many legend rows fit is the widget's own call (Hero 5 / Compact 3, from the size the layout
 * provides), so every segment is still handed over: trimming the list here would shrink the *chart*
 * to what the legend could name, and the arcs would stop adding up to the centre figure.
 */
@Composable
internal fun CategoriesDonutWidget(state: DashboardState.Content) {
    val segments = state.spentByCategory.toDonutSegments()
    SurferCategoriesDonutWidget(
        segments = segments,
        centerLabel = stringResource(Res.string.dashboard_categories_donut_center_label),
        centerValue = state.spentByCategoryTotal,
        emptyCenterText = stringResource(Res.string.dashboard_categories_donut_empty_center),
        emptyLegendText = stringResource(Res.string.dashboard_categories_donut_empty_legend),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .testTag(DashboardTestTags.CategoriesDonut),
    )
}

/**
 * The rows as arcs. Colour comes from the stored hue through the one resolver every category bubble
 * goes through, so a category keeps the same colour here, in the spend rows, and on the screens
 * that own it.
 *
 * The uncategorized bucket has no stored appearance, so — exactly as the spend rows do — its tint is
 * hashed from the fixed [DONUT_UNCATEGORIZED_ID] rather than from the label beside it: the label is
 * translated, and a wedge that changed colour with the app language would read as another category.
 */
@Composable
private fun List<CategorySpendUi>.toDonutSegments(): List<SurferDonutSegment> {
    val fallbackName = stringResource(Res.string.dashboard_spent_by_category_uncategorized)
    val segments = map { row ->
        val id = row.categoryId ?: UNCATEGORIZED_ID
        SurferDonutSegment(
            label = row.name ?: fallbackName,
            percent = row.share,
            color = SurferCategoryPalette.tintForHue(row.hue ?: NO_STORED_HUE)
                ?: SurferCategoryPalette.tintFor(id),
        )
    }
    // Rows that all measure zero are no chart: every arc would sweep nothing, and the widget
    // draws its empty track only for an empty list — so a period whose whole spend is 0.00 would
    // render as a hole with a legend of 0% rows instead of the empty state it has copy for.
    return if (segments.any { it.percent > 0f }) segments else emptyList()
}
