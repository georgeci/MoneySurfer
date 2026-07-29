package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.feature.budget.BudgetUi
import com.georgeci.moneysurfer.feature.budget.details.BudgetDetailsContent
import com.georgeci.moneysurfer.feature.budget.details.BudgetDetailsState
import io.kotest.core.spec.style.StringSpec

/**
 * Desktop UI cover for the one thing about percent signs that cannot be read off a resource file:
 * what Compose Multiplatform actually renders.
 *
 * `stringResource(res, args)` does **not** unescape `%%` the way Android's resource formatter does
 * — it substitutes the `%1$d`-style placeholders and passes every other character through, so a
 * `%%` written out of Android habit reaches the user as two percent signs. That shipped in
 * `budget_details_alert_warn_format`, `budget_details_alert_warn_message_format` and
 * `category_details_share_percent` until it was found while building the dashboard's
 * spent-by-category card, which had copied the same habit.
 *
 * Nothing below the rendered text can catch this: the resource file, the format call and the
 * argument are all individually correct, and only the composed string is wrong. Hence a UI test for
 * what looks like a string constant.
 */
@OptIn(ExperimentalTestApi::class)
class PercentFormattingTest : StringSpec({

    "the near-limit alert renders one percent sign per percentage, not two" {
        runComposeUiTest {
            setContent {
                BudgetDetailsContent(
                    state = BudgetDetailsState.Content(budget = warnBudget(), transactions = emptyList()),
                    onEvent = {},
                )
            }

            // Both halves of the banner interpolate a percentage; `78%%` and `80%%` are what a
            // regression looks like, and neither is a substring of the text asserted here.
            onNodeWithText("78% of the limit spent").assertIsDisplayed()
            onNodeWithText("Your alert is set at 80%.").assertIsDisplayed()
        }
    }
})

private const val WARN_PROGRESS = 0.78f
private const val WARN_PERCENT = 78
private const val ALERT_PERCENT = 80

/**
 * A budget past its alert threshold — the only status that draws the warn banner, and so the only
 * one that reaches the two formats under test.
 */
private fun warnBudget() = BudgetUi(
    id = BudgetId("b-1"),
    name = "Groceries",
    status = BudgetStatus.WARN,
    spentFormatted = "€312.40",
    limitFormatted = "€400.00",
    remainderFormatted = "€87.60",
    isOver = false,
    progress = WARN_PROGRESS,
    alertFraction = 0.8f,
    alertPercent = ALERT_PERCENT,
    percent = WARN_PERCENT,
    period = BudgetPeriod.MONTHLY,
    windowLabel = "1 Apr – 30 Apr",
    daysLeft = 12,
    elapsedDays = 18,
    categories = emptyList(),
    isActive = true,
    hasMixedCurrency = false,
    rolloverCarryFormatted = null,
    dailyAverageFormatted = "€17.35",
    projectedTotalFormatted = "€520.50",
    perDayRemainingFormatted = "€7.30",
    overspendFormatted = "€0.00",
)
