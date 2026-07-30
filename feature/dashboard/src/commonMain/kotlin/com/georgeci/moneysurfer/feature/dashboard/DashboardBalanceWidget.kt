package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceFootnote
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceTrend
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceWidget
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_balance_empty_text
import moneysurfer.feature.dashboard.generated.resources.dashboard_balance_other_currencies
import moneysurfer.feature.dashboard.generated.resources.dashboard_balance_rates_as_of
import moneysurfer.feature.dashboard.generated.resources.dashboard_balance_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_balance_trend_month
import org.jetbrains.compose.resources.stringResource

/**
 * The headline total, what qualifies it, and how it got there.
 *
 * Lives beside the other per-widget files rather than in `DashboardScreen` because the card now
 * assembles three separate things out of the state — the figure, one of three footnotes, and the
 * month's movement — and each of them is a decision worth reading on its own.
 *
 * [variant] stays a raw key until it reaches the widget that defines the treatments: see
 * [DashboardWidget].
 */
@Composable
internal fun BalanceWidget(state: DashboardState.Content, variant: String?) {
    SurferBalanceWidget(
        title = stringResource(Res.string.dashboard_balance_title),
        balance = state.formattedTotalBalance ?: "—",
        variant = SurferBalanceVariant.fromKey(variant),
        footnote = balanceFootnote(state),
        trend = balanceTrend(state),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(DashboardTestTags.Balance),
    )
}

/**
 * Line under the headline balance. No total at all wins over everything; after that come the two
 * things that qualify the headline — balances no rate could absorb, then how old the rates that
 * built it are. Only one of them fits, and the reader who might misread the figure needs the first.
 *
 * The month delta is not in this list: it lives in [balanceTrend], so a multi-currency workspace
 * gets its trend *and* the "as of" line rather than only the one that sorted higher here.
 */
@Composable
private fun balanceFootnote(state: DashboardState.Content): SurferBalanceFootnote? = when {
    state.formattedTotalBalance == null ->
        SurferBalanceFootnote.Empty(stringResource(Res.string.dashboard_balance_empty_text))
    state.otherCurrencyTotals.isNotEmpty() -> SurferBalanceFootnote.Note(
        stringResource(
            Res.string.dashboard_balance_other_currencies,
            state.otherCurrencyTotals.joinToString(" · "),
        ),
    )
    state.ratesAsOf != null -> SurferBalanceFootnote.Note(
        stringResource(Res.string.dashboard_balance_rates_as_of, state.ratesAsOf),
    )
    else -> null
}

/**
 * The balance card's movement half — the month delta as a sentence, and the curve it moved along.
 * Null when the view model found nothing honest to say about either; the widget then draws the
 * figure alone, as it did before the trend existed.
 */
@Composable
private fun balanceTrend(state: DashboardState.Content): SurferBalanceTrend? {
    val delta = state.formattedTrendDelta
    if (delta == null && state.balanceSeries.size < 2) return null
    return SurferBalanceTrend(
        text = delta?.let { stringResource(Res.string.dashboard_balance_trend_month, it) },
        series = state.balanceSeries,
        isNegative = state.isTrendDeltaNegative,
    )
}
