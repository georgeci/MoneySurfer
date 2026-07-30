package com.georgeci.moneysurfer.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.SpendInsights
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.SurferEmptyState
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionHeader
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.chart.SurferNetTrendCard
import com.georgeci.moneysurfer.uikit.components.chart.SurferNetTrendColumn
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.widgets.SurferCategoriesDonutWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferDonutSegment
import com.georgeci.moneysurfer.uikit.widgets.SurferWidgetSize
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.insights.generated.resources.Res
import moneysurfer.feature.insights.generated.resources.insights_categories_empty_center
import moneysurfer.feature.insights.generated.resources.insights_categories_empty_legend
import moneysurfer.feature.insights.generated.resources.insights_categories_title
import moneysurfer.feature.insights.generated.resources.insights_categories_total
import moneysurfer.feature.insights.generated.resources.insights_category_share
import moneysurfer.feature.insights.generated.resources.insights_currency_excluded
import moneysurfer.feature.insights.generated.resources.insights_currency_filtered_subtitle
import moneysurfer.feature.insights.generated.resources.insights_currency_filtered_title
import moneysurfer.feature.insights.generated.resources.insights_empty_subtitle
import moneysurfer.feature.insights.generated.resources.insights_empty_title
import moneysurfer.feature.insights.generated.resources.insights_merchant_transactions
import moneysurfer.feature.insights.generated.resources.insights_merchants_empty
import moneysurfer.feature.insights.generated.resources.insights_merchants_title
import moneysurfer.feature.insights.generated.resources.insights_months_short
import moneysurfer.feature.insights.generated.resources.insights_title
import moneysurfer.feature.insights.generated.resources.insights_trend_column_a11y
import moneysurfer.feature.insights.generated.resources.insights_trend_expense
import moneysurfer.feature.insights.generated.resources.insights_trend_income
import moneysurfer.feature.insights.generated.resources.insights_trend_months
import moneysurfer.feature.insights.generated.resources.insights_trend_title
import moneysurfer.feature.insights.generated.resources.insights_uncategorized
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable selectors for the Insights screen — see docs/testing/testing-strategy.md. */
object InsightsTestTags {
    const val Root = "insights:root"
    const val List = "insights:list"
    const val Donut = "insights:donut"
    const val Trend = "insights:trend"
    const val Empty = "insights:empty"
    const val CurrencyFiltered = "insights:currencyFiltered"
}

/** How far the figures fade while the next period's rollups are still in flight. */
private const val InFlightAlpha = 0.4f

/** The "never stored" hue sentinel — anything off the colour wheel falls back to hashing the id. */
private const val NoStoredHue = -1

@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit,
    viewModel: InsightsViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            InsightsEffect.NavigateBack -> onNavigateBack()
        }
    }

    InsightsContent(state = state, onEvent = viewModel::onEvent)
}

/**
 * The stateless half of the screen. Public, like `DashboardContent`, because the desktop UI tests in
 * `:composeApp` mount it with an injected state — see docs/testing/testing-strategy.md.
 */
@Composable
fun InsightsContent(
    state: InsightsState,
    onEvent: (InsightsEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(InsightsTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.insights_title),
                onBack = { onEvent(InsightsEvent.OnBackClick) },
            )
        },
    ) { padding ->
        val content = state as? InsightsState.Content
        if (content == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .surferContentContainer()
                .padding(top = padding.calculateTopPadding())
                .testTag(InsightsTestTags.List),
            contentPadding = PaddingValues(
                start = AppTheme.spacing.default,
                end = AppTheme.spacing.default,
                top = AppTheme.spacing.medium,
                bottom = padding.calculateBottomPadding() + AppTheme.spacing.xxxLarge,
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            item(key = "period") {
                InsightsPeriodPager(state = content, onEvent = onEvent)
            }

            // Everything below is the answer for the period above, so it fades as one block while
            // the next one is queried rather than each card blinking on its own.
            val bodyModifier = Modifier.alpha(if (content.inFlight) InFlightAlpha else 1f)

            // Nothing else is drawn in this case on purpose: charts under a message explaining why
            // there is nothing to chart would read as the message being about something else.
            if (content.hiddenByBaseCurrency) {
                item(key = "currency-filtered") {
                    CurrencyFilteredState(state = content, modifier = bodyModifier)
                }
                return@LazyColumn
            }

            if (content.isEmpty) {
                item(key = "empty") {
                    SurferEmptyState(
                        title = stringResource(Res.string.insights_empty_title),
                        subtitle = stringResource(Res.string.insights_empty_subtitle),
                        icon = SurferIcons.Receipt,
                        modifier = bodyModifier.testTag(InsightsTestTags.Empty),
                    )
                }
            } else {
                categoryBreakdown(content, bodyModifier)
            }

            item(key = "trend") {
                NetTrendCard(state = content, modifier = bodyModifier)
            }

            merchantList(content, bodyModifier)
        }
    }
}

/**
 * The donut at full size plus every category behind it.
 *
 * The donut's own legend tops out at five entries by design; a screen whose whole job is the full
 * picture lists the rest under it rather than rounding them into an implicit "other".
 */
private fun LazyListScope.categoryBreakdown(
    state: InsightsState.Content,
    modifier: Modifier,
) {
    item(key = "categories-header") {
        SurferSectionHeader(
            title = stringResource(Res.string.insights_categories_title),
            modifier = modifier,
        )
    }
    item(key = "donut") {
        CategoriesDonut(state = state, modifier = modifier.testTag(InsightsTestTags.Donut))
    }
    // Keyed by position as well as id, because the id is not guaranteed unique: a slice whose
    // stored `categoryId` names no category row — a dangling reference, or a workspace that has not
    // pulled the category yet — arrives here indistinguishable from the genuinely uncategorized
    // bucket, and two rows sharing a key is a `LazyColumn` crash rather than a cosmetic clash.
    itemsIndexed(
        state.categories,
        key = { index, category -> "category:$index:${category.id}" },
    ) { _, category ->
        val name = category.name ?: stringResource(Res.string.insights_uncategorized)
        InsightsAmountRow(
            title = name,
            amount = category.spentFormatted,
            caption = stringResource(Res.string.insights_category_share, category.sharePercent),
            leadingTint = category.tint(),
            modifier = modifier,
        )
    }
    if (state.hiddenCurrencies.isNotEmpty()) {
        item(key = "categories-excluded") {
            SectionCaption(
                text = stringResource(
                    Res.string.insights_currency_excluded,
                    state.hiddenCurrencies.joinToString(", "),
                ),
                modifier = modifier,
            )
        }
    }
}

/** Who took the most, or the line that says why nobody is named. */
private fun LazyListScope.merchantList(
    state: InsightsState.Content,
    modifier: Modifier,
) {
    item(key = "merchants-header") {
        SurferSectionHeader(
            title = stringResource(Res.string.insights_merchants_title),
            modifier = modifier,
        )
    }
    if (state.merchants.isEmpty()) {
        item(key = "merchants-empty") {
            SectionCaption(
                text = stringResource(Res.string.insights_merchants_empty),
                modifier = modifier,
            )
        }
        return
    }
    items(state.merchants, key = { "merchant:${it.merchant}" }) { merchant ->
        InsightsAmountRow(
            title = merchant.merchant,
            amount = merchant.spentFormatted,
            caption = pluralStringResource(
                Res.plurals.insights_merchant_transactions,
                merchant.transactionCount,
                merchant.transactionCount,
            ),
            modifier = modifier,
        )
    }
}

@Composable
private fun CategoriesDonut(state: InsightsState.Content, modifier: Modifier = Modifier) {
    val uncategorized = stringResource(Res.string.insights_uncategorized)
    SurferCategoriesDonutWidget(
        segments = state.categories.map { category ->
            SurferDonutSegment(
                label = category.name ?: uncategorized,
                percent = category.share,
                color = category.tint(),
            )
        },
        // The screen is the full-size view of the dashboard's widget, so it asks for the hero
        // layout explicitly rather than inheriting whatever the surrounding composition provides.
        size = SurferWidgetSize.Expanded,
        centerLabel = stringResource(Res.string.insights_categories_total),
        centerValue = state.totalFormatted,
        emptyCenterText = stringResource(Res.string.insights_categories_empty_center),
        emptyLegendText = stringResource(Res.string.insights_categories_empty_legend),
        modifier = modifier.fillMaxWidth().height(DonutHeight),
    )
}

@Composable
private fun NetTrendCard(state: InsightsState.Content, modifier: Modifier = Modifier) {
    val shortMonths = stringArrayResource(Res.array.insights_months_short)
    SurferNetTrendCard(
        title = stringResource(Res.string.insights_trend_title),
        trailingLabel = stringResource(Res.string.insights_trend_months, SpendInsights.MONTH_COLUMNS),
        incomeLabel = stringResource(Res.string.insights_trend_income),
        expenseLabel = stringResource(Res.string.insights_trend_expense),
        columns = state.months.map { month ->
            val name = shortMonths[month.monthNumber - 1]
            SurferNetTrendColumn(
                label = name,
                income = month.income,
                expense = month.expense,
                contentDescription = stringResource(
                    Res.string.insights_trend_column_a11y,
                    name,
                    month.year,
                    month.incomeFormatted,
                    month.expenseFormatted,
                ),
            )
        },
        modifier = modifier.testTag(InsightsTestTags.Trend),
    )
}

/**
 * The empty state that names the base-currency filter as the reason.
 *
 * A blank donut in a mixed-currency workspace reads as a bug rather than as the policy it is, so the
 * copy states which currency counts and which ones were left out — the one thing no chart on the
 * screen can express, because the aggregates applied the filter before the figures got here.
 */
@Composable
private fun CurrencyFilteredState(state: InsightsState.Content, modifier: Modifier = Modifier) {
    SurferEmptyState(
        title = stringResource(Res.string.insights_currency_filtered_title, state.baseCurrency),
        subtitle = stringResource(
            Res.string.insights_currency_filtered_subtitle,
            state.hiddenCurrencies.joinToString(", "),
        ),
        icon = SurferIcons.Globe,
        modifier = modifier.testTag(InsightsTestTags.CurrencyFiltered),
    )
}

/** One amount line: an optional tint dot, a name, what it took, and what that is a share of. */
@Composable
private fun InsightsAmountRow(
    title: String,
    amount: String,
    caption: String,
    modifier: Modifier = Modifier,
    leadingTint: Color? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        if (leadingTint != null) {
            Box(
                modifier = Modifier
                    .size(DotSize)
                    .clip(CircleShape)
                    .background(leadingTint),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                style = AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = amount,
            style = AppTheme.typography.titleSmall,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.materialColors.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The same resolver every category bubble goes through: the stored hue snapped to the palette,
 * falling back to hashing the id for a row that stored none — so a category keeps one colour across
 * every screen, and the uncategorized bucket keeps its own when the user switches language.
 */
@Composable
private fun InsightsCategoryUi.tint(): Color =
    SurferCategoryPalette.tintForHue(hue ?: NoStoredHue) ?: SurferCategoryPalette.tintFor(id)

private val DonutHeight = 200.dp
private val DotSize = 10.dp

@Preview
@Composable
private fun InsightsContentPreview() {
    AppTheme {
        InsightsContent(state = previewInsightsContent(), onEvent = {})
    }
}

@Preview
@Composable
private fun InsightsCurrencyFilteredPreview() {
    AppTheme {
        InsightsContent(state = previewCurrencyFilteredContent(), onEvent = {})
    }
}
