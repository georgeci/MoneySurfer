package com.georgeci.moneysurfer.utils

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private class TestViewModel : MviViewModel<String, Unit, Unit>(initialState = "") {

    override fun onEvent(event: Unit) = Unit

    fun failUnhandled(error: Throwable) = launch { throw error }

    fun failHandled(error: Throwable, onError: (Throwable) -> Unit) =
        launch(onError = onError) { throw error }

    fun awaitCancellation(onError: (Throwable) -> Unit) =
        launch(onError = onError) { CompletableDeferred<Unit>().await() }
}

class MviViewModelTest : StringSpec({

    // viewModelScope dispatches on Main; unconfined so the failing block runs eagerly
    // and the assertion doesn't depend on advancing a second scheduler.
    beforeTest { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterTest { Dispatchers.resetMain() }

    "a failure with no handler escalates to the global error boundary" {
        runTest {
            UnhandledErrors.errors.test {
                TestViewModel().failUnhandled(IllegalStateException("boom"))

                awaitItem().message shouldBe "boom"
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a failure with a handler is left to the screen" {
        runTest {
            var handled: Throwable? = null
            val failure = IllegalStateException("boom")

            UnhandledErrors.errors.test {
                TestViewModel().failHandled(failure) { handled = it }

                handled shouldBe failure
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // Cancelling a scope is how structured concurrency unwinds, not an app failure:
    // treating it as one would raise the global boundary on every screen exit.
    "cancelling the view model is not reported as an error" {
        runTest {
            var handled: Throwable? = null

            UnhandledErrors.errors.test {
                // Go through a ViewModelStore rather than poking the scope directly:
                // this is the real teardown a screen leaving composition triggers.
                val store = ViewModelStore()
                val viewModel = TestViewModel()
                store.put("test", viewModel)

                viewModel.awaitCancellation { handled = it }
                store.clear()

                handled shouldBe null
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
