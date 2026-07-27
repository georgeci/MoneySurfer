package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.preferences.NumberFormatStyle
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.feature.settings.preferences.PreferencePicker
import com.georgeci.moneysurfer.feature.settings.preferences.PreferencesContent
import com.georgeci.moneysurfer.feature.settings.preferences.PreferencesEvent
import com.georgeci.moneysurfer.feature.settings.preferences.PreferencesState
import com.georgeci.moneysurfer.feature.settings.preferences.PreferencesTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private val CATALOGUE = listOf(
    Currency(CurrencyCode("EUR"), symbol = "€", displayName = "Euro"),
    Currency(CurrencyCode("PLN"), symbol = "zł", displayName = "Polish Zloty"),
)

/**
 * Desktop UI cover for the Preferences screen — see docs/testing/testing-strategy.md.
 *
 * The reason this file exists: issue #370 was pills wired to fixed string resources, and a
 * ViewModel test cannot tell a pill bound to state from one that ignores it. Every assertion here
 * targets a label the screen composes from the value itself — the number-format sample and the
 * `CODE · symbol` currency label — rather than a `stringResource`, so a passing assertion means
 * the state reached the pill and not that a resource happened to load.
 */
@OptIn(ExperimentalTestApi::class)
class PreferencesScreenStateTest : StringSpec({

    "the number-format pill renders the stored style, not a fixed sample" {
        runComposeUiTest {
            setContent {
                PreferencesContent(
                    state = PreferencesState(numberFormat = NumberFormatStyle.DotGroupCommaDecimal),
                    onEvent = {},
                )
            }

            onNodeWithTag(PreferencesTestTags.Root).assertIsDisplayed()
            onNodeWithText("1.234,56").assertIsDisplayed()
            // The value the old hard-coded pill always showed. Its absence is the regression guard.
            onNodeWithText("1,234.56").assertDoesNotExist()
        }
    }

    "the currency pill resolves the stored code against the catalogue" {
        runComposeUiTest {
            setContent {
                PreferencesContent(
                    state = PreferencesState(currency = CurrencyCode("PLN"), currencies = CATALOGUE),
                    onEvent = {},
                )
            }

            onNodeWithText("PLN · zł").assertIsDisplayed()
            onNodeWithText("EUR · €").assertDoesNotExist()
        }
    }

    "a stored code outside the catalogue is shown bare rather than as a neighbour" {
        runComposeUiTest {
            setContent {
                PreferencesContent(
                    state = PreferencesState(currency = CurrencyCode("CHF"), currencies = CATALOGUE),
                    onEvent = {},
                )
            }

            onNodeWithText("CHF").assertIsDisplayed()
        }
    }

    "tapping a row asks for that row's picker" {
        runComposeUiTest {
            val events = mutableListOf<PreferencesEvent>()
            setContent {
                PreferencesContent(state = PreferencesState(), onEvent = { events += it })
            }

            onNodeWithTag(PreferencesTestTags.row(PreferencePicker.WeekStart)).performClick()
            onNodeWithTag(PreferencesTestTags.row(PreferencePicker.Currency)).performClick()

            events shouldContainExactly listOf(
                PreferencesEvent.OnPickerOpen(PreferencePicker.WeekStart),
                PreferencesEvent.OnPickerOpen(PreferencePicker.Currency),
            )
        }
    }

    "tapping a toggle row asks for the opposite of what it currently shows" {
        runComposeUiTest {
            val events = mutableListOf<PreferencesEvent>()
            setContent {
                PreferencesContent(
                    state = PreferencesState(hideAmounts = true),
                    onEvent = { events += it },
                )
            }

            onNodeWithTag(PreferencesTestTags.toggle("hide_amounts")).performClick()

            events.single() shouldBe PreferencesEvent.OnHideAmountsToggle(enabled = false)
        }
    }
})
