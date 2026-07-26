package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import co.touchlab.kermit.Severity
import com.georgeci.moneysurfer.domain.logging.DebugLogEntry
import com.georgeci.moneysurfer.feature.settings.debug.DebugLogContent
import com.georgeci.moneysurfer.feature.settings.debug.DebugLogTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Desktop UI cover for the QA log panel — see docs/testing/testing-strategy.md.
 *
 * The empty branch is the reason this file exists: a release build never fills the buffer, and a
 * restored back stack can still land here, so "no entries" has to read as an explanation rather
 * than a blank screen with a Clear button under it.
 */
@OptIn(ExperimentalTestApi::class)
class DebugLogScreenStateTest : StringSpec({

    "recorded entries render with the clear action" {
        runComposeUiTest {
            setContent {
                DebugLogContent(entries = listOf(ERROR_ENTRY), onBack = {}, onClear = {})
            }

            onNodeWithTag(DebugLogTestTags.Root).assertIsDisplayed()
            onNodeWithTag(DebugLogTestTags.ClearRow).assertIsDisplayed()
            onAllNodesWithTag(DebugLogTestTags.Empty).assertCountEquals(0)
        }
    }

    "an empty buffer explains itself instead of offering a clear action" {
        runComposeUiTest {
            setContent {
                DebugLogContent(entries = emptyList(), onBack = {}, onClear = {})
            }

            onNodeWithTag(DebugLogTestTags.Empty).assertIsDisplayed()
            onAllNodesWithTag(DebugLogTestTags.Root).assertCountEquals(0)
            onAllNodesWithTag(DebugLogTestTags.ClearRow).assertCountEquals(0)
        }
    }

    "the clear row reports back to the caller" {
        runComposeUiTest {
            var cleared = 0
            setContent {
                DebugLogContent(entries = listOf(ERROR_ENTRY), onBack = {}, onClear = { cleared++ })
            }

            onNodeWithTag(DebugLogTestTags.ClearRow).performClick()

            cleared shouldBe 1
        }
    }
})

private val ERROR_ENTRY = DebugLogEntry(
    severity = Severity.Error,
    tag = "SyncCoordinator",
    message = "upload failed",
    throwable = IllegalStateException("boom"),
)
