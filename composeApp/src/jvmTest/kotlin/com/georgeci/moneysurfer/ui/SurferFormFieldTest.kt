package com.georgeci.moneysurfer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.uikit.components.SurferCurrencyBottomSheetContent
import com.georgeci.moneysurfer.uikit.components.SurferCurrencyBottomSheetTestTags
import com.georgeci.moneysurfer.uikit.components.SurferCurrencyOption
import com.georgeci.moneysurfer.uikit.components.SurferTextField
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Desktop UI cover for the shared form controls — the parts of them that are about *layout*, which
 * is where they had been going wrong: a validation error used to push the rest of a form down the
 * screen, and the currency sheet used to resize itself as the user typed into its search field.
 */
@OptIn(ExperimentalTestApi::class)
class SurferFormFieldTest : StringSpec({

    "the message line under a field is reserved, so an error does not move what follows" {
        val clean = followingRowTop(errorText = null)
        val failed = followingRowTop(errorText = "Enter your email")

        failed shouldBe clean
    }

    "an error replaces the helper text under the same field" {
        runComposeUiTest {
            setContent {
                SurferComponentPreview {
                    SurferTextField(
                        value = "",
                        onValueChange = {},
                        label = "Opening balance",
                        helperText = HELPER,
                        fieldTestTag = FIELD,
                    )
                }
            }

            messageText().assertTextEquals(HELPER)
        }

        runComposeUiTest {
            setContent {
                SurferComponentPreview {
                    SurferTextField(
                        value = "-1",
                        onValueChange = {},
                        label = "Opening balance",
                        helperText = HELPER,
                        errorText = ERROR,
                        fieldTestTag = FIELD,
                    )
                }
            }

            messageText().assertTextEquals(ERROR)
        }
    }

    "the message under a field starts at the field's own left edge" {
        runComposeUiTest {
            setContent {
                SurferComponentPreview {
                    SurferTextField(
                        value = "",
                        onValueChange = {},
                        label = "Opening balance",
                        helperText = HELPER,
                        fieldTestTag = FIELD,
                    )
                }
            }

            // M3's own `supportingText` slot indents by 16dp, which reads as a stray offset under
            // a field the design aligns flush; this is why the slot is drawn by hand.
            val field = onNodeWithTag(FIELD).fetchSemanticsNode().positionInRoot.x
            val message = onNodeWithTag(MESSAGE, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.x
            message shouldBe field
        }
    }

    "searching the currency sheet filters the rows without resizing the list" {
        runComposeUiTest {
            setContent {
                SurferComponentPreview {
                    SurferCurrencyBottomSheetContent(
                        title = "Currency",
                        searchPlaceholder = "Search currency",
                        currencies = CURRENCIES,
                        selectedCode = "EUR",
                        onSelect = {},
                    )
                }
            }

            val before = onNodeWithTag(SurferCurrencyBottomSheetTestTags.List)
                .fetchSemanticsNode().size.height
            onNodeWithTag(SurferCurrencyBottomSheetTestTags.Search).performTextInput("eu")
            waitForIdle()

            onNodeWithTag(SurferCurrencyBottomSheetTestTags.List).assertIsDisplayed()
            onNodeWithTag(SurferCurrencyBottomSheetTestTags.List)
                .fetchSemanticsNode().size.height shouldBe before
        }
    }
})

/** The message slot is a box holding at most one line — the text itself is its only child. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.messageText(): SemanticsNodeInteraction =
    onNodeWithTag(MESSAGE, useUnmergedTree = true).onChildren().onFirst()

private const val FIELD = "form:field"
private const val MESSAGE = "$FIELD:error"
private const val FOLLOWING = "form:following"
private const val HELPER = "You can adjust this later."
private const val ERROR = "Cannot be negative."

/** Where the row under a field lands, so two states can be compared for layout shift. */
@OptIn(ExperimentalTestApi::class)
private fun followingRowTop(errorText: String?): Float {
    var top = Float.NaN
    runComposeUiTest {
        setContent {
            SurferComponentPreview {
                Column {
                    SurferTextField(
                        value = "",
                        onValueChange = {},
                        label = "Email",
                        errorText = errorText,
                        fieldTestTag = FIELD,
                    )
                    Text(text = "Next control", modifier = Modifier.testTag(FOLLOWING))
                }
            }
        }
        top = onNodeWithTag(FOLLOWING).fetchSemanticsNode().positionInRoot.y
    }
    return top
}

private val CURRENCIES = listOf(
    SurferCurrencyOption(code = "EUR", symbol = "€", name = "Euro"),
    SurferCurrencyOption(code = "USD", symbol = "$", name = "US Dollar"),
    SurferCurrencyOption(code = "GBP", symbol = "£", name = "British Pound"),
    SurferCurrencyOption(code = "PLN", symbol = "zł", name = "Polish Zloty"),
)
