package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.feature.settings.sync.SyncContent
import com.georgeci.moneysurfer.feature.settings.sync.SyncEvent
import com.georgeci.moneysurfer.feature.settings.sync.SyncState
import com.georgeci.moneysurfer.feature.settings.sync.SyncTestTags
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import com.georgeci.moneysurfer.sync.repository.SyncMeta
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import com.georgeci.moneysurfer.sync.api.SyncState as LiveSyncState

private val WORKSPACE = WorkspaceId("ws-1")
private val AT = Instant.fromEpochMilliseconds(1_700_000_000_000)

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

    "every hero variant renders — queued and running also drive the live section" {
        HERO_STATES.forEach { (name, state) ->
            withClue("hero state: $name") {
                runComposeUiTest {
                    setContent { SyncContent(state = state, onEvent = {}) }

                    onNodeWithTag(SyncTestTags.Hero).assertIsDisplayed()
                    onNodeWithTag(SyncTestTags.Live).assertExists()
                }
            }
        }
    }

    "each coordinator step maps to its own label, so a copy-pasted branch shows up here" {
        // The step → resource mapping is a hand-written `when` with no compile-time link between the
        // step and its string; naming the expected fragment is what catches a wrong pairing.
        STEP_LABELS.forEach { (step, fragment) ->
            withClue("step: $step") {
                runComposeUiTest {
                    setContent { SyncContent(state = running(step), onEvent = {}) }

                    onAllNodesWithText(fragment, substring = true)
                        .fetchSemanticsNodes().shouldNotBeEmpty()
                }
            }
        }
    }

    "each sync error maps to its own message, and the two carrying server text pass it through" {
        SyncErrorCases.all.forEach { (error, fragment) ->
            withClue("error: $error") {
                runComposeUiTest {
                    setContent {
                        SyncContent(
                            state = SyncState(
                                workspaceId = WORKSPACE,
                                lastOutcome = LastSyncOutcome.Failed(error, AT),
                            ),
                            onEvent = {},
                        )
                    }

                    onAllNodesWithText(fragment, substring = true)
                        .fetchSemanticsNodes().shouldNotBeEmpty()
                }
            }
        }
    }

    "a root-collection outbox row and a truncated queue both render their own wording" {
        runComposeUiTest {
            setContent {
                SyncContent(
                    state = SyncState(
                        workspaceId = WORKSPACE,
                        syncEnabled = true,
                        // One past the render limit: the extra row is what marks the list truncated.
                        outbox = List(PendingMutationQueue.DEFAULT_OUTBOX_LIMIT + 1) { index ->
                            mutation("m-$index").copy(scopeKey = null)
                        },
                    ),
                    onEvent = {},
                )
            }

            onNodeWithTag(SyncTestTags.outboxRow("m-0")).assertExists()
            // The last row is past the limit and must not be rendered at all.
            onAllNodesWithTag(
                SyncTestTags.outboxRow("m-${PendingMutationQueue.DEFAULT_OUTBOX_LIMIT}"),
            ).assertCountEquals(0)
            onAllNodesWithText("root collection", substring = true)
                .fetchSemanticsNodes().shouldNotBeEmpty()
        }
    }
})

private fun running(step: SyncStep) = SyncState(
    workspaceId = WORKSPACE,
    live = LiveSyncState.Running(
        requestId = SyncRequestId("req-1"),
        reasons = setOf(SyncReason.MANUAL),
        scope = SyncScope.AllUserData,
        currentStep = step,
    ),
)

private val HERO_STATES = listOf(
    "idle" to SyncState(workspaceId = WORKSPACE),
    "no workspace" to SyncState(workspaceId = null),
    "queued" to SyncState(workspaceId = WORKSPACE, live = LiveSyncState.Queued(count = 2)),
    "running" to running(SyncStep.PullingRemoteChanges),
    "done" to SyncState(
        workspaceId = WORKSPACE,
        lastOutcome = LastSyncOutcome.Success(SyncSummary(uploadedCount = 1, downloadedCount = 2), AT),
    ),
    "cancelled" to SyncState(workspaceId = WORKSPACE, lastOutcome = LastSyncOutcome.Cancelled(AT)),
    "failed" to SyncState(
        workspaceId = WORKSPACE,
        lastOutcome = LastSyncOutcome.Failed(SyncError.NetworkUnavailable, AT),
    ),
)

/** Every [SyncStep] the coordinator can emit, paired with a fragment unique to its own label. */
private val STEP_LABELS = listOf(
    SyncStep.WaitingForNetwork to "waiting for network",
    SyncStep.Started to "started",
    SyncStep.UploadingPendingChanges to "uploading changes",
    SyncStep.UploadingEntity("TRANSACTION", current = 2, total = 7) to "uploading TRANSACTION 2/7",
    SyncStep.PullingRemoteChanges to "downloading changes",
    SyncStep.PullingCollection("accounts") to "downloading accounts",
    SyncStep.RecalculatingProjections to "recalculating balances",
    SyncStep.Completed(SyncSummary()) to "completed",
    SyncStep.Cancelled() to "cancelled",
    SyncStep.Failed(SyncError.AuthRequired) to "failed",
)

private object SyncErrorCases {
    val all = listOf(
        SyncError.Cancelled to "Cancelled",
        SyncError.NetworkUnavailable to "No internet connection",
        SyncError.AuthRequired to "sign in again",
        // Plain apostrophe on purpose: compose-resources keeps the backslash of Android's \' escape.
        SyncError.PermissionDenied to "You don't have access",
        SyncError.StorageError(IllegalStateException("disk")) to "Local storage error",
        // These two carry text authored elsewhere — the screen has to show it, not a generic line.
        SyncError.UnsupportedAppVersion("Update to 2.4 to keep syncing") to "Update to 2.4 to keep syncing",
        SyncError.Unknown(IllegalStateException("FAILED_PRECONDITION")) to "FAILED_PRECONDITION",
    )
}

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
