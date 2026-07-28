package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.uikit.components.base.SurferPeriodArrow
import com.georgeci.moneysurfer.uikit.components.base.SurferPeriodPager
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transactions_list_months
import moneysurfer.feature.transaction.generated.resources.transactions_list_months_short
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_all_time
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_all_time_sub
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_mode_all_time
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_mode_month
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_mode_week
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_next
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_previous
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_week_range
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_week_range_cross_month
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_week_sub
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * The design's `PeriodPager`, plus a menu on the label: the design never drew a mode switcher, and
 * the pill is the only place on the screen where the period is already the subject.
 *
 * In all-time mode the arrows are disabled rather than the pager hidden (the design's
 * `All time · arrows disabled` variant) — hiding it would strand the user with no way back.
 */
@Composable
internal fun PeriodPager(
    state: TransactionsByAccountState.Content,
    onEvent: (TransactionsByAccountEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        SurferPeriodPager(
            label = state.period.label(),
            sublabel = state.period.sublabel(),
            previous = SurferPeriodArrow(
                onClick = { onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick) },
                enabled = state.canGoToPreviousPeriod,
                contentDescription = stringResource(Res.string.transactions_list_period_previous),
            ),
            next = SurferPeriodArrow(
                onClick = { onEvent(TransactionsByAccountEvent.OnNextPeriodClick) },
                enabled = state.canGoToNextPeriod,
                contentDescription = stringResource(Res.string.transactions_list_period_next),
            ),
            onLabelClick = { menuExpanded = true },
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            TransactionPeriodMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        menuExpanded = false
                        onEvent(TransactionsByAccountEvent.OnPeriodModeChanged(mode))
                    },
                )
            }
        }
    }
}

@Composable
private fun TransactionPeriodMode.label(): String = stringResource(
    when (this) {
        TransactionPeriodMode.Month -> Res.string.transactions_list_period_mode_month
        TransactionPeriodMode.Week -> Res.string.transactions_list_period_mode_week
        TransactionPeriodMode.AllTime -> Res.string.transactions_list_period_mode_all_time
    },
)

@Composable
private fun TransactionPeriodUi.label(): String = when (this) {
    is TransactionPeriodUi.Month -> monthNames()[monthNumber - 1]
    is TransactionPeriodUi.Week -> weekRangeLabel(from, to)
    TransactionPeriodUi.AllTime -> stringResource(Res.string.transactions_list_period_all_time)
}

@Composable
private fun TransactionPeriodUi.sublabel(): String = when (this) {
    is TransactionPeriodUi.Month -> year.toString()
    is TransactionPeriodUi.Week ->
        stringResource(Res.string.transactions_list_period_week_sub, weekNumber, weekYear)
    TransactionPeriodUi.AllTime -> stringResource(Res.string.transactions_list_period_all_time_sub)
}

/** `Mar 25 – 31`, or `Mar 30 – Apr 5` when the week straddles two months. */
@Composable
private fun weekRangeLabel(from: LocalDate, to: LocalDate): String {
    val shortMonths = stringArrayResource(Res.array.transactions_list_months_short)
    val fromMonth = shortMonths[from.month.number - 1]
    return if (from.month == to.month) {
        stringResource(Res.string.transactions_list_period_week_range, fromMonth, from.day, to.day)
    } else {
        stringResource(
            Res.string.transactions_list_period_week_range_cross_month,
            fromMonth,
            from.day,
            shortMonths[to.month.number - 1],
            to.day,
        )
    }
}

/** Standalone month names — the pager label is a sentence of its own. */
@Composable
private fun monthNames(): List<String> = stringArrayResource(Res.array.transactions_list_months)
