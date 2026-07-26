package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.feature.settings.sync.SyncContent
import com.georgeci.moneysurfer.feature.settings.sync.SyncEvent
import com.georgeci.moneysurfer.feature.settings.sync.SyncState
import com.georgeci.moneysurfer.feature.settings.sync.SyncTestTags
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.SyncMeta
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

/**
 * Desktop UI cover for the sync diagnostics panel — see docs/testing/testing-strategy.md.
 *
 * The empty states are the reason this file exists: "no outbox rows" and "no cursors" are answers,
 * not blanks, and a section that rendered nothing at all would read as a screen that failed to load
 * the very state it was built to show.
 */
@OptIn(ExperimentalTestApi::class)
class SyncScreenStateTest : StringSpec({

    "an empty workspace still renders every section, with explicit empty states" {
        runComposeUiTest {
            setContent {
                SyncContent(state = SyncState(workspaceId = WORKSPACE, syncEnabled = true), onEvent = {})
            }

            // The hero is the only section guaranteed to be above the fold; the rest are asserted
            // as present in the tree, which is what "the section rendered" means on a scrolling page.
            onNodeWithTag(SyncTestTags.Hero).assertIsDisplayed()
            onNodeWithTag(SyncTestTags.Live).assertExists()
            onNodeWithTag(SyncTestTags.LastOutcome).assertExists()
            onNodeWithTag(SyncTestTags.OutboxEmpty).assertExists()
            onNodeWithTag(SyncTestTags.CursorsEmpty).assertExists()
            onNodeWithTag(SyncTestTags.ForceSyncRow).assertExists()
            onNodeWithTag(SyncTestTags.ResetCursorsRow).assertExists()
        }
    }

    "with sync off the outbox says it was not read — never that the queue is empty" {
        // The view model stops observing the outbox then, while the rows stay in Room. Reusing the
        // empty state here would report a fact nobody checked.
        runComposeUiTest {
            setContent {
                SyncContent(state = SyncState(workspaceId = WORKSPACE, syncEnabled = false), onEvent = {})
            }

            onNodeWithTag(SyncTestTags.OutboxUnknown).assertExists()
            onAllNodesWithTag(SyncTestTags.OutboxEmpty).assertCountEquals(0)
        }
    }

    "outbox rows and cursors replace their empty states, one node per row" {
        runComposeUiTest {
            setContent {
                SyncContent(
                    state = SyncState(
                        workspaceId = WORKSPACE,
                        syncEnabled = true,
                        outbox = listOf(mutation("m-1"), mutation("m-2", lastError = "PERMISSION_DENIED")),
                        cursors = listOf(meta("accounts"), meta("transactions", lastPulledAt = null)),
                    ),
                    onEvent = {},
                )
            }

            onAllNodesWithTag(SyncTestTags.OutboxEmpty).assertCountEquals(0)
            onAllNodesWithTag(SyncTestTags.CursorsEmpty).assertCountEquals(0)
            onNodeWithTag(SyncTestTags.outboxRow("m-1")).assertExists()
            onNodeWithTag(SyncTestTags.outboxRow("m-2")).assertExists()
            onNodeWithTag(SyncTestTags.cursorRow("accounts")).assertExists()
            onNodeWithTag(SyncTestTags.cursorRow("transactions")).assertExists()
        }
    }

    "the reset-cursors row asks before clearing anything" {
        runComposeUiTest {
            val events = mutableListOf<SyncEvent>()
            setContent {
                SyncContent(state = SyncState(workspaceId = WORKSPACE, syncEnabled = true), onEvent = { events += it })
            }

            onAllNodesWithTag(SyncTestTags.ResetCursorsDialog).assertCountEquals(0)
            onNodeWithTag(SyncTestTags.ResetCursorsRow).performScrollTo().performClick()

            events shouldBe listOf(SyncEvent.OnResetCursorsClick)
        }
    }

    "the confirmation dialog is rendered from state, not from a click inside the screen" {
        runComposeUiTest {
            setContent {
                SyncContent(
                    state = SyncState(workspaceId = WORKSPACE, showResetCursorsConfirm = true),
                    onEvent = {},
                )
            }

            onNodeWithTag(SyncTestTags.ResetCursorsDialog).assertIsDisplayed()
        }
    }

    "with no workspace the reset row is inert — there is no scope to clear" {
        runComposeUiTest {
            val events = mutableListOf<SyncEvent>()
            setContent {
                SyncContent(state = SyncState(workspaceId = null), onEvent = { events += it })
            }

            onNodeWithTag(SyncTestTags.ResetCursorsRow).performScrollTo().performClick()

            events shouldBe emptyList()
        }
    }
})

private val WORKSPACE = WorkspaceId("ws-1")
private val AT = Instant.fromEpochMilliseconds(1_700_000_000_000)

private fun mutation(id: String, lastError: String? = null) = PendingMutation(
    id = id,
    entityType = "TRANSACTION",
    entityId = "tx-$id",
    operation = MutationOperation.UPDATE,
    scopeKey = WORKSPACE.value,
    createdAt = AT,
    attempts = if (lastError == null) 0 else 3,
    lastError = lastError,
)

private fun meta(collection: String, lastPulledAt: Instant? = AT) = SyncMeta(
    scopeKey = WORKSPACE.value,
    collection = collection,
    lastPulledAt = lastPulledAt,
    lastSyncSuccessAt = AT,
    lastSyncAttemptAt = AT,
)
