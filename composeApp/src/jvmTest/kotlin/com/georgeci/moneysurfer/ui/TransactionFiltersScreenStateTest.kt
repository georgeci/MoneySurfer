package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDatePreset
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilters
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import com.georgeci.moneysurfer.feature.transaction.filters.TransactionFiltersContent
import com.georgeci.moneysurfer.feature.transaction.filters.TransactionFiltersEvent
import com.georgeci.moneysurfer.feature.transaction.filters.TransactionFiltersState
import com.georgeci.moneysurfer.feature.transaction.filters.TransactionFiltersTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Desktop UI cover for the transactions filter screen — see docs/testing/testing-strategy.md.
 *
 * The screen renders a draft and nothing else: what it shows about that draft (the live result
 * count on Apply, whether Reset is offered, whether the accounts section exists at all) is decided
 * here rather than in the ViewModel, which only holds the draft itself.
 */
@OptIn(ExperimentalTestApi::class)
class TransactionFiltersScreenStateTest : StringSpec({

    "the screen opens on its sections, with the applied type already selected" {
        runComposeUiTest {
            setContent {
                TransactionFiltersContent(
                    state = filters(draft = TransactionFilters(type = TransactionTypeFilter.Income)),
                    onEvent = {},
                )
            }

            onNodeWithTag(TransactionFiltersTestTags.Root).assertIsDisplayed()
            onNodeWithText("DATE RANGE").assertIsDisplayed()
            onNodeWithText("TYPE").assertIsDisplayed()
            onNodeWithTag(typeTag(TransactionTypeFilter.Income)).assertIsSelected()
            onNodeWithTag(typeTag(TransactionTypeFilter.All)).assertIsNotSelected()
        }
    }

    "Apply carries the live count of what the list will show" {
        runComposeUiTest {
            setContent { TransactionFiltersContent(state = filters(resultCount = 47), onEvent = {}) }

            onNodeWithText("Apply · 47 results").assertIsDisplayed()
        }
    }

    "a count that hit its cap is reported as a floor, not as an exact number" {
        runComposeUiTest {
            setContent {
                TransactionFiltersContent(
                    state = filters(resultCount = 500, resultCountCapped = true),
                    onEvent = {},
                )
            }

            onNodeWithText("Apply · 500+ results").assertIsDisplayed()
        }
    }

    "Reset is offered only once the draft carries something to reset" {
        runComposeUiTest {
            setContent { TransactionFiltersContent(state = filters(), onEvent = {}) }

            onNodeWithText("Reset").assertIsNotEnabled()
        }
    }

    "the footer's two buttons are the only way the draft leaves this screen" {
        runComposeUiTest {
            val events = mutableListOf<TransactionFiltersEvent>()
            setContent { TransactionFiltersContent(state = filters(), onEvent = { events += it }) }

            onNodeWithTag(TransactionFiltersTestTags.Apply).performClick()
            onNodeWithTag(TransactionFiltersTestTags.Cancel).performClick()
            waitForIdle()

            events shouldContainExactly listOf(
                TransactionFiltersEvent.OnApplyClick,
                TransactionFiltersEvent.OnCancelClick,
            )
        }
    }

    "while the range follows the pager, the screen says so instead of showing empty date fields" {
        runComposeUiTest {
            setContent { TransactionFiltersContent(state = filters(), onEvent = {}) }

            onNodeWithText("Follows the period shown on the list.").assertIsDisplayed()
            onNodeWithText("From").assertDoesNotExist()
        }
    }

    "a custom range opens the two date fields, each with its own empty label" {
        runComposeUiTest {
            setContent {
                TransactionFiltersContent(
                    state = filters(
                        draft = TransactionFilters(
                            dateRange = TransactionDateRange.Custom(
                                from = LocalDate(2025, 1, 1),
                                to = null,
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("From").assertIsDisplayed()
            onNodeWithText("To").assertIsDisplayed()
            // An open-ended range is a legitimate thing to ask for, so the empty side says "Any".
            onNodeWithText("Any").assertIsDisplayed()
        }
    }

    "tapping a preset asks for it rather than applying it on the spot" {
        runComposeUiTest {
            val events = mutableListOf<TransactionFiltersEvent>()
            setContent { TransactionFiltersContent(state = filters(), onEvent = { events += it }) }

            onNodeWithText("Last month").performClick()
            waitForIdle()

            events shouldContainExactly listOf(
                TransactionFiltersEvent.OnDatePresetClick(TransactionDatePreset.LastMonth),
            )
        }
    }

    "an account-scoped screen drops the accounts section entirely" {
        runComposeUiTest {
            setContent { TransactionFiltersContent(state = filters(showAccounts = false), onEvent = {}) }

            // Picking another account on a list restricted to one could only ever empty it.
            onNodeWithText("ACCOUNTS").assertDoesNotExist()
            onNodeWithText("Everyday").assertDoesNotExist()
        }
    }

    "the categories section counts what is ticked against what there is" {
        runComposeUiTest {
            setContent {
                TransactionFiltersContent(
                    state = filters(draft = TransactionFilters(categoryIds = setOf(GROCERIES.id))),
                    onEvent = {},
                )
            }

            onNodeWithText("1 of 2 selected").assertIsDisplayed()
            onNodeWithText("Groceries").assertIsDisplayed()
            onNodeWithText("Salary").assertIsDisplayed()
        }
    }

    "the amount bounds and the two switches sit at the bottom of the sheet" {
        runComposeUiTest {
            val events = mutableListOf<TransactionFiltersEvent>()
            setContent { TransactionFiltersContent(state = filters(), onEvent = { events += it }) }

            onNodeWithText("Recurring only").performScrollTo().assertIsDisplayed()
            onNodeWithText("Planned only").assertIsDisplayed()
            onNodeWithText("Oldest first").performScrollTo().performClick()
            waitForIdle()

            events shouldContainExactly listOf(
                TransactionFiltersEvent.OnSortSelected(TransactionSort.Oldest),
            )
        }
    }
})

private val WORKSPACE = WorkspaceId("ws-1")

private val EVERYDAY = Account(
    id = AccountId("a-1"),
    workspaceId = WORKSPACE,
    name = "Everyday",
    type = AccountType.CASH,
    currencyCode = CurrencyCode("USD"),
    balance = Money.zero(),
)

private fun category(id: String, name: String, type: CategoryType) = Category(
    id = CategoryId(id),
    workspaceId = WORKSPACE,
    name = name,
    type = type,
    parentId = null,
    createdAt = Instant.fromEpochMilliseconds(0),
)

private val GROCERIES = category("c-1", "Groceries", CategoryType.EXPENSE)
private val SALARY = category("c-2", "Salary", CategoryType.INCOME)

private fun filters(
    draft: TransactionFilters = TransactionFilters.Empty,
    showAccounts: Boolean = true,
    resultCount: Int = 0,
    resultCountCapped: Boolean = false,
) = TransactionFiltersState(
    draft = draft,
    showAccounts = showAccounts,
    accounts = listOf(EVERYDAY),
    categories = listOf(GROCERIES, SALARY),
    resultCount = resultCount,
    resultCountCapped = resultCountCapped,
)

private fun typeTag(type: TransactionTypeFilter) =
    TransactionFiltersTestTags.TypePrefix + type.name.lowercase()
