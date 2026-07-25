package com.georgeci.moneysurfer.feature.settings.debug

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.config.ConfigDebugLayerCell
import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.ConfigDebugRowKind
import com.georgeci.moneysurfer.domain.config.DebugConfigInspector
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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

@OptIn(ExperimentalCoroutinesApi::class)
class DebugConfigViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "availability comes from the inspector, so a release build renders nothing" {
        DebugConfigViewModel(RecordingInspector(isAvailable = false)).currentState.isAvailable shouldBe false
        DebugConfigViewModel(RecordingInspector(isAvailable = true)).currentState.isAvailable shouldBe true
    }

    "a layer that failed to load is carried into state so the panel can say so" {
        // Without this the panel would present a fallback value as the real answer.
        DebugConfigViewModel(RecordingInspector(degradedLayers = listOf("Debug")))
            .currentState.degradedLayers shouldBe listOf("Debug")

        DebugConfigViewModel(RecordingInspector()).currentState.degradedLayers shouldBe emptyList()
    }

    "rows are mirrored into state as the inspector re-resolves them" {
        val inspector = RecordingInspector()
        val viewModel = DebugConfigViewModel(inspector)

        viewModel.currentState.rows.map { it.name } shouldBe listOf("panel.flag")

        inspector.emit(listOf(row("panel.flag"), row("panel.other")))

        viewModel.currentState.rows.map { it.name } shouldBe listOf("panel.flag", "panel.other")
    }

    "an accepted override reaches the inspector and posts no message" {
        runTest {
            val inspector = RecordingInspector(accept = true)
            val viewModel = DebugConfigViewModel(inspector)

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
            val viewModel = DebugConfigViewModel(inspector)

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

        DebugConfigViewModel(inspector).onEvent(DebugConfigEvent.OnClearOverride("panel.flag"))

        inspector.cleared shouldBe listOf("panel.flag")
    }

    "reset-all delegates once" {
        val inspector = RecordingInspector()

        DebugConfigViewModel(inspector).onEvent(DebugConfigEvent.OnResetAllClick)

        inspector.resetAllCount shouldBe 1
    }

    "back navigates away" {
        runTest {
            val viewModel = DebugConfigViewModel(RecordingInspector())

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(DebugConfigEvent.OnBackClick)

                awaitItem() shouldBe DebugConfigEffect.NavigateBack
            }
        }
    }
})
