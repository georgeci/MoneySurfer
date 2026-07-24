package com.georgeci.moneysurfer.feature.category.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.SurferDetailPlaceholder
import com.georgeci.moneysurfer.uikit.components.account.SurferAccountStatCard
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionHeader
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarAction
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryBreakdownRow
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryHeroCard
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryTrendBar
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryTrendCard
import com.georgeci.moneysurfer.uikit.components.transaction.SurferTransactionLine
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.category_details_edit_content_description
import moneysurfer.feature.category.generated.resources.category_details_hero_label_expense
import moneysurfer.feature.category.generated.resources.category_details_hero_label_income
import moneysurfer.feature.category.generated.resources.category_details_hero_label_transfer
import moneysurfer.feature.category.generated.resources.category_details_log_expense
import moneysurfer.feature.category.generated.resources.category_details_log_income
import moneysurfer.feature.category.generated.resources.category_details_missing
import moneysurfer.feature.category.generated.resources.category_details_recent
import moneysurfer.feature.category.generated.resources.category_details_recent_empty
import moneysurfer.feature.category.generated.resources.category_details_share_percent
import moneysurfer.feature.category.generated.resources.category_details_stat_average
import moneysurfer.feature.category.generated.resources.category_details_stat_per_transaction
import moneysurfer.feature.category.generated.resources.category_details_stat_transactions
import moneysurfer.feature.category.generated.resources.category_details_subcategories
import moneysurfer.feature.category.generated.resources.category_details_subcategories_empty
import moneysurfer.feature.category.generated.resources.category_details_title
import moneysurfer.feature.category.generated.resources.category_details_transaction_untitled
import moneysurfer.feature.category.generated.resources.category_details_trend_title
import moneysurfer.feature.category.generated.resources.month_short_april
import moneysurfer.feature.category.generated.resources.month_short_august
import moneysurfer.feature.category.generated.resources.month_short_december
import moneysurfer.feature.category.generated.resources.month_short_february
import moneysurfer.feature.category.generated.resources.month_short_january
import moneysurfer.feature.category.generated.resources.month_short_july
import moneysurfer.feature.category.generated.resources.month_short_june
import moneysurfer.feature.category.generated.resources.month_short_march
import moneysurfer.feature.category.generated.resources.month_short_may
import moneysurfer.feature.category.generated.resources.month_short_november
import moneysurfer.feature.category.generated.resources.month_short_october
import moneysurfer.feature.category.generated.resources.month_short_september
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun CategoryDetailsScreen(
    categoryId: CategoryId,
    onNavigateBack: () -> Unit,
    onNavigateToCategoryEdit: (CategoryId) -> Unit = {},
    onNavigateToCategoryDetails: (CategoryId) -> Unit = {},
    onNavigateToTransactionCreation: () -> Unit = {},
    onNavigateToTransactionDetails: (TransactionId) -> Unit = {},
    viewModel: CategoryDetailsViewModel =
        koinViewModel(key = categoryId.value) { parametersOf(categoryId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            CategoryDetailsEffect.NavigateBack -> onNavigateBack()
            CategoryDetailsEffect.NavigateToTransactionCreation -> onNavigateToTransactionCreation()
            is CategoryDetailsEffect.NavigateToCategoryEdit -> onNavigateToCategoryEdit(effect.categoryId)
            is CategoryDetailsEffect.NavigateToCategoryDetails ->
                onNavigateToCategoryDetails(effect.categoryId)
            is CategoryDetailsEffect.NavigateToTransactionDetails ->
                onNavigateToTransactionDetails(effect.transactionId)
        }
    }

    when (val current = state) {
        is CategoryDetailsState.Loading -> CategoryDetailsPlaceholder(
            message = null,
            onEvent = viewModel::onEvent,
        )
        is CategoryDetailsState.Missing -> CategoryDetailsPlaceholder(
            message = stringResource(Res.string.category_details_missing),
            onEvent = viewModel::onEvent,
        )
        is CategoryDetailsState.Content -> CategoryDetailsContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun CategoryDetailsPlaceholder(
    message: String?,
    onEvent: (CategoryDetailsEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.category_details_title),
                onBack = { onEvent(CategoryDetailsEvent.OnBackClick) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Loading passes no message: an empty frame under the toolbar reads as "still
            // arriving", where a placeholder would claim the category is gone.
            if (message != null) {
                SurferDetailPlaceholder(text = message)
            }
        }
    }
}

@Composable
private fun CategoryDetailsContent(
    state: CategoryDetailsState.Content,
    onEvent: (CategoryDetailsEvent) -> Unit,
) {
    val visual = SurferCategoryPalette.visualFor(
        id = state.categoryId.value,
        iconKey = state.iconKey,
        hue = state.hue,
        systemKind = state.systemKind,
    )
    val currentMonthLabel = state.months.lastOrNull()?.let { monthShortLabel(it.monthOfYear) }.orEmpty()

    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.category_details_title),
                onBack = { onEvent(CategoryDetailsEvent.OnBackClick) },
                actions = {
                    SurferToolbarAction(
                        icon = SurferIcons.Edit,
                        contentDescription = stringResource(
                            Res.string.category_details_edit_content_description,
                        ),
                        onClick = { onEvent(CategoryDetailsEvent.OnEditClick) },
                    )
                },
            )
        },
        floatingActionButton = {
            val label = stringResource(
                if (state.type == CategoryType.INCOME) {
                    Res.string.category_details_log_income
                } else {
                    Res.string.category_details_log_expense
                },
            )
            ExtendedFloatingActionButton(
                text = { Text(label) },
                icon = {
                    Icon(imageVector = SurferIcons.Add, contentDescription = SurferSemantics.Decorative)
                },
                onClick = { onEvent(CategoryDetailsEvent.OnAddTransactionClick) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = AppTheme.spacing.default,
                end = AppTheme.spacing.default,
                top = AppTheme.spacing.small,
                bottom = padding.calculateBottomPadding() +
                    AppTheme.spacing.xxxLarge + AppTheme.spacing.xLarge,
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            item(key = "hero") {
                SurferCategoryHeroCard(
                    icon = visual.icon,
                    tint = visual.tint,
                    name = state.name,
                    label = stringResource(heroLabelFor(state.type), currentMonthLabel),
                    formattedAmount = state.formattedTotal,
                )
            }

            item(key = "stats") {
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
                    SurferAccountStatCard(
                        label = stringResource(Res.string.category_details_stat_average),
                        value = state.formattedAverage,
                        icon = SurferIcons.Calendar,
                        iconTint = visual.tint,
                        modifier = Modifier.weight(1f),
                    )
                    SurferAccountStatCard(
                        label = stringResource(Res.string.category_details_stat_transactions),
                        value = state.transactionCount.toString(),
                        icon = SurferIcons.Receipt,
                        iconTint = visual.tint,
                        modifier = Modifier.weight(1f),
                    )
                    SurferAccountStatCard(
                        label = stringResource(Res.string.category_details_stat_per_transaction),
                        value = state.formattedPerTransaction,
                        icon = SurferIcons.Tag,
                        iconTint = visual.tint,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "trend") {
                // No trailing label — the stat tile directly above already carries the average,
                // and the mockup's "Cap …" slot went away with caps moving to Budgets.
                SurferCategoryTrendCard(
                    title = stringResource(Res.string.category_details_trend_title),
                    tint = visual.tint,
                    bars = state.months.map { month ->
                        SurferCategoryTrendBar(
                            label = monthShortLabel(month.monthOfYear),
                            value = month.minorValue,
                            formattedValue = month.formattedValue,
                        )
                    },
                )
            }

            item(key = "subcategories-header") {
                SurferSectionHeader(
                    title = stringResource(Res.string.category_details_subcategories),
                    modifier = Modifier.padding(
                        horizontal = AppTheme.spacing.small,
                        vertical = AppTheme.spacing.xSmall,
                    ),
                )
            }

            if (state.isLeaf) {
                item(key = "subcategories-empty") {
                    Text(
                        text = stringResource(Res.string.category_details_subcategories_empty),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.materialColors.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = AppTheme.spacing.small,
                            vertical = AppTheme.spacing.small,
                        ),
                    )
                }
            } else {
                items(state.subcategories, key = { "sub-${it.id.value}" }) { child ->
                    val childVisual = SurferCategoryPalette.visualFor(
                        id = child.id.value,
                        iconKey = child.iconKey,
                        hue = child.hue,
                    )
                    SurferCategoryBreakdownRow(
                        visual = childVisual,
                        name = child.name,
                        formattedAmount = child.formattedAmount,
                        sharePercentLabel = stringResource(
                            Res.string.category_details_share_percent,
                            (child.share * PercentScale).roundToInt(),
                        ),
                        share = child.share,
                        onClick = { onEvent(CategoryDetailsEvent.OnSubcategoryClick(child.id)) },
                    )
                }
            }

            item(key = "recent-header") {
                SurferSectionHeader(
                    title = stringResource(Res.string.category_details_recent),
                    modifier = Modifier.padding(
                        horizontal = AppTheme.spacing.small,
                        vertical = AppTheme.spacing.xSmall,
                    ),
                )
            }

            if (state.transactions.isEmpty()) {
                item(key = "recent-empty") {
                    Text(
                        text = stringResource(Res.string.category_details_recent_empty),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.materialColors.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = AppTheme.spacing.small,
                            vertical = AppTheme.spacing.small,
                        ),
                    )
                }
            } else {
                items(state.transactions, key = { it.id.value }) { txn ->
                    val untitled = stringResource(Res.string.category_details_transaction_untitled)
                    SurferTransactionLine(
                        icon = visual.icon,
                        title = txn.title.ifBlank { untitled },
                        formattedAmount = txn.formattedAmount,
                        categoryHueSeed = txn.categoryHueSeed,
                        amountColor = if (txn.isExpense) {
                            AppTheme.materialColors.onSurface
                        } else {
                            AppTheme.semanticColors.income
                        },
                        onClick = { onEvent(CategoryDetailsEvent.OnTransactionClick(txn.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun heroLabelFor(type: CategoryType) = when (type) {
    CategoryType.INCOME -> Res.string.category_details_hero_label_income
    CategoryType.TRANSFER -> Res.string.category_details_hero_label_transfer
    CategoryType.EXPENSE -> Res.string.category_details_hero_label_expense
}

@Composable
private fun monthShortLabel(month: Month): String = stringResource(
    when (month) {
        Month.JANUARY -> Res.string.month_short_january
        Month.FEBRUARY -> Res.string.month_short_february
        Month.MARCH -> Res.string.month_short_march
        Month.APRIL -> Res.string.month_short_april
        Month.MAY -> Res.string.month_short_may
        Month.JUNE -> Res.string.month_short_june
        Month.JULY -> Res.string.month_short_july
        Month.AUGUST -> Res.string.month_short_august
        Month.SEPTEMBER -> Res.string.month_short_september
        Month.OCTOBER -> Res.string.month_short_october
        Month.NOVEMBER -> Res.string.month_short_november
        Month.DECEMBER -> Res.string.month_short_december
    },
)

private const val PercentScale = 100

@Preview
@Composable
private fun CategoryDetailsContentPreview() {
    AppTheme {
        CategoryDetailsContent(
            state = CategoryDetailsState.Content(
                categoryId = CategoryId("dining"),
                name = "Dining",
                type = CategoryType.EXPENSE,
                iconKey = "receipt",
                hue = 340,
                systemKind = null,
                isLeaf = false,
                formattedTotal = "€168.55",
                formattedAverage = "€196.83",
                formattedPerTransaction = "€12.04",
                transactionCount = 14,
                months = listOf(
                    CategoryTrendMonthUi(YearMonth(2025, 11), 20500, "€205.00"),
                    CategoryTrendMonthUi(YearMonth(2025, 12), 19200, "€192.00"),
                    CategoryTrendMonthUi(YearMonth(2026, 1), 24000, "€240.00"),
                    CategoryTrendMonthUi(YearMonth(2026, 2), 19800, "€198.00"),
                    CategoryTrendMonthUi(YearMonth(2026, 3), 17600, "€176.00"),
                    CategoryTrendMonthUi(YearMonth(2026, 4), 16855, "€168.55"),
                ),
                subcategories = listOf(
                    CategorySubcategoryUi(
                        id = CategoryId("d-delivery"),
                        name = "Delivery",
                        iconKey = "receipt",
                        hue = 340,
                        formattedAmount = "€106.15",
                        share = 0.63f,
                    ),
                    CategorySubcategoryUi(
                        id = CategoryId("d-coffee"),
                        name = "Coffee",
                        iconKey = "cash",
                        hue = 35,
                        formattedAmount = "€62.40",
                        share = 0.37f,
                    ),
                ),
                transactions = listOf(
                    CategoryTransactionUi(
                        id = TransactionId("1"),
                        title = "Sushi Bar",
                        formattedAmount = "€38.20",
                        isExpense = true,
                        categoryHueSeed = "dining",
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
