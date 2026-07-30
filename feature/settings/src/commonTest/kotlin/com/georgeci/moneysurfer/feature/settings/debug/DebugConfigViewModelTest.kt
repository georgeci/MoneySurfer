package com.georgeci.moneysurfer.feature.settings.debug

import app.cash.turbine.test
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.georgeci.moneysurfer.domain.config.ConfigDebugLayerCell
import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.ConfigDebugRowKind
import com.georgeci.moneysurfer.domain.config.DebugConfigInspector
import com.georgeci.moneysurfer.domain.debug.DebugDataPrefiller
import com.georgeci.moneysurfer.domain.debug.DebugPrefillError
import com.georgeci.moneysurfer.domain.debug.DebugPrefillReport
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private fun row(name: String, value: String = "true") = ConfigDebugRow(
    name = name,
    effectiveValue = value,
    winner = "Build",
    kind = ConfigDebugRowKind.Bool,
    overridden = false,
    layers = listOf(ConfigDebugLayerCell("Build", value, undecodable = false)),
)

/**
 * Records what reaches the inspector and lets a test decide whether a write is accepted, which is
 * the only branch in the view model worth pinning: an undecodable value has to surface as a message
 * rather than as a silently stored value nothing can read back.
 */
private class RecordingInspector(
    override val isAvailable: Boolean = true,
    override val degradedLayers: List<String> = emptyList(),
    private val accept: Boolean = true,
    initialRows: List<ConfigDebugRow> = listOf(row("panel.flag")),
) : DebugConfigInspector {

    private val rowState = MutableStateFlow(initialRows)
    val overrides: MutableList<Pair<String, String>> = mutableListOf()
    val cleared: MutableList<String> = mutableListOf()
    var resetAllCount: Int = 0
        private set

    override val rows: Flow<List<ConfigDebugRow>> = rowState

    override suspend fun override(name: String, raw: String): Result<Unit> {
        overrides += name to raw
        return if (accept) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("'$raw' is not valid for '$name'"))
        }
    }

    override suspend fun clearOverride(name: String) {
        cleared += name
    }

    override suspend fun resetAll() {
        resetAllCount++
    }

    fun emit(rows: List<ConfigDebugRow>) {
        rowState.value = rows
    }
}

/**
 * Answers with whatever the test asked for and counts the runs, so the specs can pin the two things
 * the view model owns here: that a run in flight blocks a second one, and that each failure shape
 * reaches the panel as its own message.
 */
private class RecordingPrefiller(
    private val answer: Either<DebugPrefillError, DebugPrefillReport> =
        DebugPrefillReport(accounts = 3, categories = 0, transactions = 300, budgets = 2, goals = 2).right(),
    private val gate: CompletableDeferred<Unit>? = null,
    /** Not every failure comes back as a `Left` — the prefiller reads the session outside its catch. */
    private val boom: Throwable? = null,
) : DebugDataPrefiller {

    var runs: Int = 0
        private set

    override suspend fun prefill(): Either<DebugPrefillError, DebugPrefillReport> {
        runs++
        gate?.await()
        boom?.let { throw it }
        return answer
    }
}

private fun panel(
    inspector: DebugConfigInspector = RecordingInspector(),
    prefiller: DebugDataPrefiller = RecordingPrefiller(),
) = DebugConfigViewModel(inspector, prefiller)

@OptIn(ExperimentalCoroutinesApi::class)
class DebugConfigViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "availability comes from the inspector, so a release build renders nothing" {
        panel(RecordingInspector(isAvailable = false)).currentState.isAvailable shouldBe false
        panel(RecordingInspector(isAvailable = true)).currentState.isAvailable shouldBe true
    }

    "a layer that failed to load is carried into state so the panel can say so" {
        // Without this the panel would present a fallback value as the real answer.
        panel(RecordingInspector(degradedLayers = listOf("Debug")))
            .currentState.degradedLayers shouldBe listOf("Debug")

        panel(RecordingInspector()).currentState.degradedLayers shouldBe emptyList()
    }

    "rows are mirrored into state as the inspector re-resolves them" {
        val inspector = RecordingInspector()
        val viewModel = panel(inspector)

        viewModel.currentState.rows.map { it.name } shouldBe listOf("panel.flag")

        inspector.emit(listOf(row("panel.flag"), row("panel.other")))

        viewModel.currentState.rows.map { it.name } shouldBe listOf("panel.flag", "panel.other")
    }

    "an accepted override reaches the inspector and posts no message" {
        runTest {
            val inspector = RecordingInspector(accept = true)
            val viewModel = panel(inspector)

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(DebugConfigEvent.OnOverride("panel.flag", "false"))

                inspector.overrides shouldBe listOf("panel.flag" to "false")
                expectNoEvents()
            }
        }
    }

    "a rejected override surfaces the offending value instead of failing silently" {
        runTest {
            val inspector = RecordingInspector(accept = false)
            val viewModel = panel(inspector)

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(DebugConfigEvent.OnOverride("panel.choice", "Sepia"))

                awaitItem() shouldBe DebugConfigEffect.NotifyInvalidValue(
                    name = "panel.choice",
                    raw = "Sepia",
                )
            }
        }
    }

    "clearing an override delegates the key name" {
        val inspector = RecordingInspector()

        panel(inspector).onEvent(DebugConfigEvent.OnClearOverride("panel.flag"))

        inspector.cleared shouldBe listOf("panel.flag")
    }

    "reset-all delegates once" {
        val inspector = RecordingInspector()

        panel(inspector).onEvent(DebugConfigEvent.OnResetAllClick)

        inspector.resetAllCount shouldBe 1
    }

    "back navigates away" {
        runTest {
            val viewModel = panel(RecordingInspector())

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(DebugConfigEvent.OnBackClick)

                awaitItem() shouldBe DebugConfigEffect.NavigateBack
            }
        }
    }

    "a finished prefill reports what it wrote" {
        runTest {
            val viewModel = panel()

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(DebugConfigEvent.OnPrefillClick)

                val outcome = (awaitItem() as DebugConfigEffect.NotifyPrefillResult).outcome
                (outcome as PrefillOutcome.Done).report.transactions shouldBe 300
            }
            viewModel.currentState.inFlight shouldBe false
        }
    }

    "each prefill failure keeps its own shape, because they call for different actions" {
        runTest {
            val noWorkspace = panel(prefiller = RecordingPrefiller(DebugPrefillError.NoWorkspace.left()))
            val broken = panel(
                prefiller = RecordingPrefiller(DebugPrefillError.WriteFailed(IllegalStateException("db")).left()),
            )

            noWorkspace.sideEffects.effectFlow.test {
                noWorkspace.onEvent(DebugConfigEvent.OnPrefillClick)

                awaitItem() shouldBe DebugConfigEffect.NotifyPrefillResult(PrefillOutcome.NoWorkspace)
            }
            broken.sideEffects.effectFlow.test {
                broken.onEvent(DebugConfigEvent.OnPrefillClick)

                awaitItem() shouldBe DebugConfigEffect.NotifyPrefillResult(PrefillOutcome.Failed)
            }
        }
    }

    "a throw releases the row instead of stranding it on 'Writing…' forever" {
        runTest {
            // The row expresses "busy" by dropping its click handler, so a flag left at true is
            // not a cosmetic glitch — it makes the action unreachable until the screen is rebuilt.
            val prefiller = RecordingPrefiller(boom = IllegalStateException("session store unreadable"))
            val viewModel = panel(prefiller = prefiller)

            viewModel.onEvent(DebugConfigEvent.OnPrefillClick)

            viewModel.currentState.inFlight shouldBe false
            // …and the action is genuinely usable again, not just reporting that it is.
            viewModel.onEvent(DebugConfigEvent.OnPrefillClick)
            prefiller.runs shouldBe 2
        }
    }

    "a second tap while a run is in flight is ignored rather than doubling the batch" {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val prefiller = RecordingPrefiller(gate = gate)
            val viewModel = panel(prefiller = prefiller)

            viewModel.onEvent(DebugConfigEvent.OnPrefillClick)
            viewModel.currentState.inFlight shouldBe true

            viewModel.onEvent(DebugConfigEvent.OnPrefillClick)

            prefiller.runs shouldBe 1
            gate.complete(Unit)
        }
    }

    "the logs row navigates without touching the inspector" {
        runTest {
            val inspector = RecordingInspector()
            val viewModel = panel(inspector)

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(DebugConfigEvent.OnLogsClick)

                awaitItem() shouldBe DebugConfigEffect.NavigateToLogs
            }

            // The log buffer is not configuration: nothing about this row may reach the layers.
            inspector.overrides.shouldBeEmpty()
            inspector.cleared.shouldBeEmpty()
            inspector.resetAllCount shouldBe 0
        }
    }
})
