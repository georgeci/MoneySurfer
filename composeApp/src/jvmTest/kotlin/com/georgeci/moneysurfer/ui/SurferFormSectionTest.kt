package com.georgeci.moneysurfer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.uikit.components.base.SurferFormSection
import com.georgeci.moneysurfer.uikit.components.base.SurferRoundIconButton
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe

/**
 * Desktop UI cover for the two controls the walkthrough fixes added to `:uikit`.
 *
 * Both are about layout and input rather than state, so a view-model test cannot reach them.
 */
@OptIn(ExperimentalTestApi::class)
class SurferFormSectionTest : StringSpec({

    "a caption sits closer to its own control than to the next block" {
        runComposeUiTest {
            setContent {
                SurferComponentPreview {
                    // The form spacing that used to land between a caption and its own field,
                    // because the two were siblings of this Column rather than one block.
                    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.large)) {
                        SurferFormSection(label = "Type") {
                            Text(text = "type control", modifier = Modifier.testTag(FIRST_CONTROL))
                        }
                        SurferFormSection(label = "Currency") {
                            Text(text = "currency control", modifier = Modifier.testTag(SECOND_CONTROL))
                        }
                    }
                }
            }

            val firstLabel = onNodeWithTag(FIRST_CONTROL).fetchSemanticsNode().positionInRoot.y
            val secondLabel = onNodeWithTag(SECOND_CONTROL).fetchSemanticsNode().positionInRoot.y
            val labelToControl = firstLabel - onNodeWithText("Type").fetchSemanticsNode().positionInRoot.y
            val blockToBlock = onNodeWithText("Currency").fetchSemanticsNode().positionInRoot.y - firstLabel

            labelToControl shouldBeLessThan blockToBlock
            // Sanity: the second block really is below the first.
            (secondLabel > firstLabel) shouldBe true
        }
    }

    "the round button reports its tap and names the action it performs" {
        runComposeUiTest {
            var clicks = 0
            setContent {
                SurferComponentPreview {
                    SurferRoundIconButton(
                        icon = SurferIcons.Remove,
                        contentDescription = "Remove Goals from the dashboard",
                        onClick = { clicks++ },
                        modifier = Modifier.testTag(BUTTON),
                    )
                }
            }

            onNodeWithTag(BUTTON).performClick()
            waitForIdle()

            clicks shouldBe 1
            // The glyph alone says nothing; the row's widget name has to reach a screen reader,
            // which it does through the button's merged semantics rather than the icon's own node.
            onNodeWithTag(BUTTON).assertContentDescriptionEquals("Remove Goals from the dashboard")
        }
    }
})

private const val FIRST_CONTROL = "form:first"
private const val SECOND_CONTROL = "form:second"
private const val BUTTON = "form:round"
