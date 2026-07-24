package com.georgeci.moneysurfer.utils

import app.cash.turbine.test
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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
})
